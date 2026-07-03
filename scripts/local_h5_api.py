#!/usr/bin/env python3
import hashlib
import os
import uuid
from datetime import datetime

import pymysql
from flask import Flask, jsonify, request, send_from_directory
from werkzeug.utils import secure_filename


APP_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UPLOAD_DIR = os.path.join(APP_ROOT, "uploads")


def env(name, default=""):
    value = os.environ.get(name, default)
    return value if value is not None else default


def db_conn():
    return pymysql.connect(
        host=env("DB_HOST", "127.0.0.1"),
        port=int(env("DB_PORT", "13306")),
        user=env("DB_USER", "root"),
        password=env("DB_PASSWORD", ""),
        database=env("DB_NAME", "nbys-activity-manager"),
        charset="utf8mb4",
        autocommit=True,
        cursorclass=pymysql.cursors.DictCursor,
    )


def ok(data):
    return jsonify({"code": 0, "message": "ok", "data": data})


def fail(message, code=1, status=400):
    return jsonify({"code": code, "message": message, "data": None}), status


def text(value):
    return "" if value is None else str(value).strip()


def invite_code(user):
    base = f"{text(user.get('callsign'))}{text(user.get('username'))}"
    return hashlib.md5(base.encode("utf-8")).hexdigest()[:10]


def permissions(role):
    base = {"activity:view", "venue:view", "gameMode:view", "user:view", "role:view", "permission:view", "attendance:view"}
    if role in {"superadmin", "admin"}:
        return sorted(base | {
            "activity:create", "activity:update", "activity:delete", "activity:cancel", "activity:restore",
            "plan:create", "plan:update", "plan:delete",
            "venue:create", "venue:update", "venue:delete", "gameMode:create", "gameMode:update", "gameMode:delete",
            "user:update", "user:delete", "user:disable", "user:resetPassword", "role:update", "permission:update",
            "attendance:create", "attendance:update", "attendance:delete", "attendance:export",
        })
    if role == "activity_admin":
        return sorted(base | {"activity:create", "activity:update", "activity:delete", "activity:cancel", "activity:restore", "plan:create", "plan:update", "plan:delete"})
    if role == "attendance_admin":
        return sorted(base | {"attendance:create", "attendance:update", "attendance:delete", "attendance:export"})
    return sorted(base)


def scrypt_matches(raw, encoded):
    if not encoded or not encoded.startswith("scrypt:"):
        return False
    try:
        method, salt, digest = encoded.split("$", 2)
        _, n, r, p = method.split(":")
        actual = hashlib.scrypt(
            raw.encode("utf-8"),
            salt=salt.encode("utf-8"),
            n=int(n),
            r=int(r),
            p=int(p),
            maxmem=128 * 1024 * 1024,
            dklen=64,
        )
        return actual.hex() == digest
    except Exception:
        return False


def public_user(user):
    if not user:
        return None
    out = dict(user)
    out.pop("password_hash", None)
    out["invite_code"] = invite_code(out)
    out["permissions"] = permissions(text(out.get("role")))
    return out


sessions = {}
app = Flask(__name__, static_folder=None)


def current_user():
    header = request.headers.get("Authorization", "")
    if not header.startswith("Bearer "):
        return None
    token = header[7:]
    user_id = sessions.get(token)
    if user_id is None and "." in token:
        try:
            user_id = int(token.split(".", 1)[0])
        except Exception:
            return None
    if user_id is None:
        return None
    with db_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("select * from users where id=%s", (user_id,))
            return cur.fetchone()


@app.get("/api/h5/me")
def me():
    user = current_user()
    if not user:
        return fail("未登录", code=401, status=401)
    return ok(public_user(user))


@app.post("/api/h5/auth/register")
def register():
    payload = request.get_json(silent=True) or {}
    username = text(payload.get("username"))
    callsign = text(payload.get("callsign"))
    password = text(payload.get("password"))
    avatar_url = text(payload.get("avatar_url"))
    invite = text(payload.get("invite_code"))
    if not username or not password:
        return fail("用户名、密码不能为空")
    with db_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("select id from users where username=%s", (username,))
            if cur.fetchone():
                return fail("用户名已存在")
            if callsign:
                cur.execute("select id from users where callsign=%s", (callsign,))
                if cur.fetchone():
                    return fail("呼号已存在")
            invited_by_id = None
            if invite:
                cur.execute("select id,username,callsign from users where disabled=0")
                for row in cur.fetchall():
                    if invite_code(row).lower() == invite.lower():
                        invited_by_id = row["id"]
                        break
                if invite and invited_by_id is None:
                    return fail("邀请码不存在")

            cur.execute(
                """
                insert into users(
                    username,callsign,avatar_url,password_hash,role,disabled,
                    is_regular_member,attendance_manager,extraction_authorized,extraction_manager,
                    invited_by_id,created_at,last_seen
                ) values(%s,%s,%s,%s,'user',0,0,0,0,0,%s,now(),now())
                """,
                (username, callsign, avatar_url, encode_password(password), invited_by_id),
            )
    return ok(None)


def encode_password(raw):
    salt_bytes = os.urandom(16)
    salt = salt_bytes.hex()[:16]
    digest = hashlib.scrypt(
        raw.encode("utf-8"),
        salt=salt.encode("utf-8"),
        n=32768,
        r=8,
        p=1,
        maxmem=128 * 1024 * 1024,
        dklen=64,
    ).hex()
    return f"scrypt:32768:8:1${salt}${digest}"


@app.post("/api/h5/auth/login")
def login():
    payload = request.get_json(silent=True) or {}
    account = text(payload.get("account"))
    password = text(payload.get("password"))
    with db_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "select * from users where disabled=0 and (username=%s or callsign=%s) limit 1",
                (account, account),
            )
            user = cur.fetchone()
            if not user or not scrypt_matches(password, text(user.get("password_hash"))):
                return fail("账号或密码错误", code=401, status=401)
            token = f"{user['id']}.{uuid.uuid4().hex}"
            sessions[token] = user["id"]
            out = public_user(user)
            out["token"] = token
            return ok(out)


@app.get("/api/h5/activities")
def activities():
    return ok([])


@app.get("/api/h5/activity-plans")
def activity_plans():
    return ok([])


@app.get("/api/h5/launcher-rentals/my-items")
def my_items():
    return ok([])


@app.get("/api/h5/notifications")
def notifications():
    return ok([])


@app.post("/api/h5/files/upload")
def upload():
    file = request.files.get("file")
    if not file:
        return fail("文件不能为空")
    original = file.filename or "file"
    _, ext = os.path.splitext(original)
    name = f"{uuid.uuid4().hex}{ext}"
    os.makedirs(UPLOAD_DIR, exist_ok=True)
    path = os.path.join(UPLOAD_DIR, secure_filename(name))
    file.save(path)
    return ok({"fileName": name, "url": f"/uploads/{name}", "size": os.path.getsize(path)})


@app.get("/uploads/<path:file_name>")
def upload_file(file_name):
    return send_from_directory(UPLOAD_DIR, file_name)


@app.errorhandler(Exception)
def handle_error(err):
    msg = getattr(err, "description", None) or str(err)
    code = 500
    if isinstance(err, ValueError):
        code = 400
    return fail(msg, code=code, status=code)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(env("SERVER_PORT", "8080")), debug=False, threaded=True)
