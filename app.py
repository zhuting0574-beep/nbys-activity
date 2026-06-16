import os
import secrets
import string
from datetime import datetime, timedelta, time
from functools import wraps

from flask import Flask, jsonify, redirect, render_template, request, url_for, flash
from flask_login import LoginManager, UserMixin, current_user, login_required, login_user, logout_user
from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import check_password_hash, generate_password_hash

APP_TITLE = "宁波甬士活动管理系统"
JOB_OPTIONS = ["突击兵", "支援兵", "医疗兵", "狙击手", "弹药兵", "填线兵"]
ACTIVITY_REGION_OPTIONS = ["宁波", "外地"]
ACTIVITY_VISIBILITY_OPTIONS = {
    "official_only": "仅正式队员可见",
    "all": "所有人可见",
    "official_plus_invite": "正式队员和邀请队员可见",
}

BASE_DIR = os.path.abspath(os.path.dirname(__file__))
DATA_DIR = os.environ.get("DATA_DIR", os.path.join(BASE_DIR, "data"))
os.makedirs(DATA_DIR, exist_ok=True)

app = Flask(__name__)
app.config["SECRET_KEY"] = os.environ.get("SECRET_KEY", secrets.token_hex(24))
app.config["SQLALCHEMY_DATABASE_URI"] = os.environ.get(
    "DATABASE_URL", f"sqlite:///{os.path.join(DATA_DIR, 'app.db')}"
)
app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False
app.config["UPLOAD_FOLDER"] = os.path.join(BASE_DIR, "static", "uploads", "launchers")
os.makedirs(app.config["UPLOAD_FOLDER"], exist_ok=True)

# SQLite 在 NAS / Docker 场景下足够轻量，后续可平滑迁移到 PostgreSQL。
db = SQLAlchemy(app)
login_manager = LoginManager(app)
login_manager.login_view = "login"
login_manager.login_message = "请先登录。"


class User(UserMixin, db.Model):
    __tablename__ = "users"
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(80), unique=True, nullable=False, index=True)
    callsign = db.Column(db.String(80), nullable=False, index=True)
    password_hash = db.Column(db.String(255), nullable=False)
    role = db.Column(db.String(20), nullable=False, default="user")  # user/admin/superadmin
    disabled = db.Column(db.Boolean, default=False, nullable=False)
    is_regular_member = db.Column(db.Boolean, default=False, nullable=False)
    attendance_manager = db.Column(db.Boolean, default=False, nullable=False)
    extraction_authorized = db.Column(db.Boolean, default=False, nullable=False)
    extraction_manager = db.Column(db.Boolean, default=False, nullable=False)
    invited_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    invite_code_id = db.Column(db.Integer, db.ForeignKey("invite_codes.id"), nullable=True)
    inviter = db.relationship("User", remote_side=[id], foreign_keys=[invited_by_id])
    invite_code = db.relationship("InviteCode", foreign_keys=[invite_code_id])
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)
    last_seen = db.Column(db.DateTime, default=datetime.now, nullable=False)

    def set_password(self, password: str) -> None:
        self.password_hash = generate_password_hash(password)

    def check_password(self, password: str) -> bool:
        return check_password_hash(self.password_hash, password)

    @property
    def is_admin(self) -> bool:
        return self.role in {"admin", "superadmin"} or bool(self.attendance_manager) or bool(self.extraction_manager)

    @property
    def is_superadmin(self) -> bool:
        return self.role == "superadmin"

    @property
    def can_manage_attendance(self) -> bool:
        return self.is_superadmin or bool(self.attendance_manager)

    @property
    def can_manage_extraction(self) -> bool:
        return self.is_superadmin or bool(self.extraction_manager)

    @property
    def can_access_extraction(self) -> bool:
        return self.can_manage_extraction or bool(self.extraction_authorized)


class InviteCode(db.Model):
    __tablename__ = "invite_codes"
    id = db.Column(db.Integer, primary_key=True)
    code = db.Column(db.String(32), unique=True, nullable=False, index=True)
    role = db.Column(db.String(20), nullable=False, default="user")
    created_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    used_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    max_uses = db.Column(db.Integer, default=1, nullable=False)
    used_count = db.Column(db.Integer, default=0, nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)
    used_at = db.Column(db.DateTime, nullable=True)

    created_by = db.relationship("User", foreign_keys=[created_by_id])
    used_by = db.relationship("User", foreign_keys=[used_by_id])

    @property
    def is_used(self) -> bool:
        return self.used_count >= self.max_uses

    @property
    def remaining_uses(self) -> int:
        return max(self.max_uses - self.used_count, 0)


class Activity(db.Model):
    __tablename__ = "activities"
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(160), nullable=False)
    start_at = db.Column(db.DateTime, nullable=False)
    end_at = db.Column(db.DateTime, nullable=False)
    location = db.Column(db.String(255), nullable=False)
    open_min = db.Column(db.Integer, nullable=False)
    camp_count = db.Column(db.Integer, nullable=False)
    camp_limit = db.Column(db.Integer, nullable=False)
    squad_count = db.Column(db.Integer, nullable=False)
    squad_limit = db.Column(db.Integer, nullable=False)
    allowed_jobs = db.Column(db.String(255), nullable=False)
    game_modes = db.Column(db.Text, nullable=True)
    attendance_enabled = db.Column(db.Boolean, default=False, nullable=False)
    activity_region = db.Column(db.String(20), default="宁波", nullable=False)
    visibility_type = db.Column(db.String(32), default="all", nullable=False)
    invitee_ids = db.Column(db.Text, nullable=True)
    deleted_at = db.Column(db.DateTime, nullable=True)
    deleted_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    list_locked = db.Column(db.Boolean, default=False, nullable=False)
    created_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)

    created_by = db.relationship("User", foreign_keys=[created_by_id])
    deleted_by = db.relationship("User", foreign_keys=[deleted_by_id])
    enrollments = db.relationship("Enrollment", back_populates="activity", cascade="all, delete-orphan")

    @property
    def is_deleted(self) -> bool:
        return self.deleted_at is not None

    @property
    def allowed_job_list(self):
        return [x for x in self.allowed_jobs.split(",") if x]

    @property
    def game_mode_list(self):
        return [x for x in (self.game_modes or "").split(",") if x]


class Venue(db.Model):
    __tablename__ = "venues"
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(120), nullable=False, unique=True)
    address = db.Column(db.String(255), nullable=False)
    created_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)

    created_by = db.relationship("User")


class GameMode(db.Model):
    __tablename__ = "game_modes"
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(120), nullable=False, unique=True)
    suitable_people = db.Column(db.String(80), nullable=False)
    rules = db.Column(db.Text, nullable=False)
    created_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)

    created_by = db.relationship("User")


class ActivityPlan(db.Model):
    __tablename__ = "activity_plans"
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(160), nullable=False)
    vote_deadline = db.Column(db.DateTime, nullable=False)
    hidden = db.Column(db.Boolean, default=False, nullable=False)
    converted_activity_id = db.Column(db.Integer, db.ForeignKey("activities.id"), nullable=True)
    visibility_type = db.Column(db.String(32), default="all", nullable=False)
    invitee_ids = db.Column(db.Text, nullable=True)
    created_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)

    created_by = db.relationship("User", foreign_keys=[created_by_id])
    converted_activity = db.relationship("Activity")
    date_options = db.relationship("PlanDateOption", back_populates="plan", cascade="all, delete-orphan")
    venue_options = db.relationship("PlanVenueOption", back_populates="plan", cascade="all, delete-orphan")
    mode_options = db.relationship("PlanGameModeOption", back_populates="plan", cascade="all, delete-orphan")
    votes = db.relationship("PlanVote", back_populates="plan", cascade="all, delete-orphan")


class PlanDateOption(db.Model):
    __tablename__ = "plan_date_options"
    id = db.Column(db.Integer, primary_key=True)
    plan_id = db.Column(db.Integer, db.ForeignKey("activity_plans.id"), nullable=False, index=True)
    date = db.Column(db.Date, nullable=False)
    note = db.Column(db.String(80), nullable=True)
    plan = db.relationship("ActivityPlan", back_populates="date_options")


class PlanVenueOption(db.Model):
    __tablename__ = "plan_venue_options"
    id = db.Column(db.Integer, primary_key=True)
    plan_id = db.Column(db.Integer, db.ForeignKey("activity_plans.id"), nullable=False, index=True)
    venue_id = db.Column(db.Integer, db.ForeignKey("venues.id"), nullable=False)
    plan = db.relationship("ActivityPlan", back_populates="venue_options")
    venue = db.relationship("Venue")


class PlanGameModeOption(db.Model):
    __tablename__ = "plan_game_mode_options"
    id = db.Column(db.Integer, primary_key=True)
    plan_id = db.Column(db.Integer, db.ForeignKey("activity_plans.id"), nullable=False, index=True)
    game_mode_id = db.Column(db.Integer, db.ForeignKey("game_modes.id"), nullable=False)
    plan = db.relationship("ActivityPlan", back_populates="mode_options")
    game_mode = db.relationship("GameMode")


class PlanVote(db.Model):
    __tablename__ = "plan_votes"
    id = db.Column(db.Integer, primary_key=True)
    plan_id = db.Column(db.Integer, db.ForeignKey("activity_plans.id"), nullable=False, index=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    option_type = db.Column(db.String(20), nullable=False)  # date/venue/mode
    option_id = db.Column(db.Integer, nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)

    plan = db.relationship("ActivityPlan", back_populates="votes")
    user = db.relationship("User")
    __table_args__ = (db.UniqueConstraint("plan_id", "user_id", "option_type", "option_id", name="uq_plan_user_option"),)


class CampSetting(db.Model):
    __tablename__ = "camp_settings"
    id = db.Column(db.Integer, primary_key=True)
    activity_id = db.Column(db.Integer, db.ForeignKey("activities.id"), nullable=False, index=True)
    camp_no = db.Column(db.Integer, nullable=False)
    commander_user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    updated_at = db.Column(db.DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)

    activity = db.relationship("Activity")
    commander = db.relationship("User")
    __table_args__ = (db.UniqueConstraint("activity_id", "camp_no", name="uq_activity_camp_setting"),)


class SquadSetting(db.Model):
    __tablename__ = "squad_settings"
    id = db.Column(db.Integer, primary_key=True)
    activity_id = db.Column(db.Integer, db.ForeignKey("activities.id"), nullable=False, index=True)
    camp_no = db.Column(db.Integer, nullable=False)
    squad_no = db.Column(db.Integer, nullable=False)
    name = db.Column(db.String(80), nullable=False)
    radio_channel = db.Column(db.String(30), nullable=False)
    leader_user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    locked = db.Column(db.Boolean, default=False, nullable=False)
    updated_at = db.Column(db.DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)

    activity = db.relationship("Activity")
    leader = db.relationship("User")
    __table_args__ = (db.UniqueConstraint("activity_id", "camp_no", "squad_no", name="uq_activity_squad_setting"),)




class LauncherRentalItem(db.Model):
    __tablename__ = "launcher_rental_items"
    id = db.Column(db.Integer, primary_key=True)
    # 历史字段名保留为 owner_type，界面显示为“发射器所有人”，避免旧数据库迁移复杂。
    owner_type = db.Column(db.String(80), nullable=False, default="")
    name = db.Column(db.String(120), nullable=False)
    description = db.Column(db.String(50), nullable=True)
    photo_filename = db.Column(db.String(255), nullable=True)
    rent_fee = db.Column(db.String(50), nullable=False, default="0")
    active = db.Column(db.Boolean, default=True, nullable=False)
    created_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)
    updated_at = db.Column(db.DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)

    created_by = db.relationship("User")


class ActivityLauncherOption(db.Model):
    __tablename__ = "activity_launcher_options"
    id = db.Column(db.Integer, primary_key=True)
    activity_id = db.Column(db.Integer, db.ForeignKey("activities.id"), nullable=False, index=True)
    launcher_id = db.Column(db.Integer, db.ForeignKey("launcher_rental_items.id"), nullable=False, index=True)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)

    activity = db.relationship("Activity")
    launcher = db.relationship("LauncherRentalItem")
    __table_args__ = (db.UniqueConstraint("activity_id", "launcher_id", name="uq_activity_launcher_option"),)


class LauncherRentalSetting(db.Model):
    __tablename__ = "launcher_rental_settings"
    id = db.Column(db.Integer, primary_key=True)
    note = db.Column(db.Text, nullable=True)
    updated_at = db.Column(db.DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)


class ActivityLauncherRental(db.Model):
    __tablename__ = "activity_launcher_rentals"
    id = db.Column(db.Integer, primary_key=True)
    activity_id = db.Column(db.Integer, db.ForeignKey("activities.id"), nullable=False, index=True)
    launcher_id = db.Column(db.Integer, db.ForeignKey("launcher_rental_items.id"), nullable=False, index=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    rented_at = db.Column(db.DateTime, default=datetime.now, nullable=False)
    cancelled_at = db.Column(db.DateTime, nullable=True)

    activity = db.relationship("Activity")
    launcher = db.relationship("LauncherRentalItem")
    user = db.relationship("User")



class ExtractionSeason(db.Model):
    __tablename__ = "extraction_seasons"
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(120), nullable=False)
    start_date = db.Column(db.Date, nullable=False)
    end_date = db.Column(db.Date, nullable=False)
    active = db.Column(db.Boolean, default=True, nullable=False)
    kill_reward_cash = db.Column(db.Integer, default=0, nullable=False)
    created_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)

    created_by = db.relationship("User")


class ExtractionItemDef(db.Model):
    __tablename__ = "extraction_item_defs"
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(120), nullable=False)
    level = db.Column(db.String(20), nullable=False, default="普通")
    item_category = db.Column(db.String(20), nullable=False, default="常规物品")
    min_price = db.Column(db.Integer, nullable=False, default=1)
    max_price = db.Column(db.Integer, nullable=False, default=1)
    width = db.Column(db.Integer, nullable=False, default=1)
    height = db.Column(db.Integer, nullable=False, default=1)
    photo_filename = db.Column(db.String(255), nullable=True)
    active = db.Column(db.Boolean, default=True, nullable=False)
    created_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)

    created_by = db.relationship("User")


class ExtractionItemPrice(db.Model):
    __tablename__ = "extraction_item_prices"
    id = db.Column(db.Integer, primary_key=True)
    item_def_id = db.Column(db.Integer, db.ForeignKey("extraction_item_defs.id"), nullable=False, index=True)
    price_date = db.Column(db.Date, nullable=False, index=True)
    price = db.Column(db.Integer, nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)

    item_def = db.relationship("ExtractionItemDef")
    __table_args__ = (db.UniqueConstraint("item_def_id", "price_date", name="uq_extraction_item_price_date"),)


class ExtractionUserProfile(db.Model):
    __tablename__ = "extraction_user_profiles"
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, unique=True, index=True)
    cash = db.Column(db.Integer, default=0, nullable=False)
    storage_rows = db.Column(db.Integer, default=6, nullable=False)
    storage_cols = db.Column(db.Integer, default=10, nullable=False)
    runs_count = db.Column(db.Integer, default=0, nullable=False)
    updated_at = db.Column(db.DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)

    user = db.relationship("User")


class ExtractionInventoryItem(db.Model):
    __tablename__ = "extraction_inventory_items"
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    item_def_id = db.Column(db.Integer, db.ForeignKey("extraction_item_defs.id"), nullable=False)
    season_id = db.Column(db.Integer, db.ForeignKey("extraction_seasons.id"), nullable=True, index=True)
    location = db.Column(db.String(20), default="buffer", nullable=False)  # buffer/storage
    row = db.Column(db.Integer, nullable=True)
    col = db.Column(db.Integer, nullable=True)
    sold_at = db.Column(db.DateTime, nullable=True)
    sold_price = db.Column(db.Integer, nullable=True)
    durability_percent = db.Column(db.Integer, default=100, nullable=False)
    match_participant_id = db.Column(db.Integer, db.ForeignKey("extraction_match_participants.id"), nullable=True, index=True)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)

    user = db.relationship("User")
    item_def = db.relationship("ExtractionItemDef")
    season = db.relationship("ExtractionSeason")


class ExtractionShopItem(db.Model):
    __tablename__ = "extraction_shop_items"
    id = db.Column(db.Integer, primary_key=True)
    category = db.Column(db.String(20), nullable=False)  # storage/item
    name = db.Column(db.String(120), nullable=False)
    price = db.Column(db.Integer, nullable=False, default=0)
    stock = db.Column(db.Integer, nullable=False, default=0)
    shelf_until = db.Column(db.Date, nullable=True)
    storage_rows = db.Column(db.Integer, nullable=True)
    storage_cols = db.Column(db.Integer, nullable=True)
    item_def_id = db.Column(db.Integer, db.ForeignKey("extraction_item_defs.id"), nullable=True)
    active = db.Column(db.Boolean, default=True, nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)

    item_def = db.relationship("ExtractionItemDef")


class ExtractionRunRecord(db.Model):
    __tablename__ = "extraction_run_records"
    id = db.Column(db.Integer, primary_key=True)
    activity_id = db.Column(db.Integer, db.ForeignKey("activities.id"), nullable=False, index=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)

    activity = db.relationship("Activity")
    user = db.relationship("User")
    __table_args__ = (db.UniqueConstraint("activity_id", "user_id", name="uq_extraction_run_activity_user"),)




class ExtractionClassRule(db.Model):
    __tablename__ = "extraction_class_rules"
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(80), nullable=False, unique=True)
    health = db.Column(db.Integer, nullable=False, default=100)
    maintenance_fee = db.Column(db.Integer, nullable=False, default=0)
    sort_order = db.Column(db.Integer, nullable=False, default=0)
    active = db.Column(db.Boolean, default=True, nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)


class ExtractionWeaponRule(db.Model):
    __tablename__ = "extraction_weapon_rules"
    id = db.Column(db.Integer, primary_key=True)
    weapon_type = db.Column(db.String(20), nullable=False, default="knife")  # knife/regular/special
    name = db.Column(db.String(120), nullable=False)
    item_def_id = db.Column(db.Integer, db.ForeignKey("extraction_item_defs.id"), nullable=True, index=True)
    usage_fee = db.Column(db.Integer, nullable=False, default=0)
    durability_cost_percent = db.Column(db.Integer, nullable=False, default=0)
    active = db.Column(db.Boolean, default=True, nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)

    item_def = db.relationship("ExtractionItemDef")


class ExtractionMatch(db.Model):
    __tablename__ = "extraction_matches"
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(160), nullable=False)
    venue_name = db.Column(db.String(160), nullable=True)
    squad_count = db.Column(db.Integer, nullable=False, default=1)
    squad_limit = db.Column(db.Integer, nullable=False, default=5)
    status = db.Column(db.String(20), nullable=False, default="preparing")  # preparing/started/ended/cancelled
    created_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)
    started_at = db.Column(db.DateTime, nullable=True)
    ended_at = db.Column(db.DateTime, nullable=True)

    created_by = db.relationship("User")
    participants = db.relationship("ExtractionMatchParticipant", back_populates="match", cascade="all, delete-orphan")


class ExtractionMatchParticipant(db.Model):
    __tablename__ = "extraction_match_participants"
    id = db.Column(db.Integer, primary_key=True)
    match_id = db.Column(db.Integer, db.ForeignKey("extraction_matches.id"), nullable=False, index=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    squad_no = db.Column(db.Integer, nullable=True)
    class_rule_id = db.Column(db.Integer, db.ForeignKey("extraction_class_rules.id"), nullable=True)
    weapon_rule_id = db.Column(db.Integer, db.ForeignKey("extraction_weapon_rules.id"), nullable=True)
    weapon_inventory_item_id = db.Column(db.Integer, db.ForeignKey("extraction_inventory_items.id"), nullable=True)
    locked = db.Column(db.Boolean, default=False, nullable=False)
    paid_cash = db.Column(db.Integer, nullable=False, default=0)
    evacuated = db.Column(db.Boolean, nullable=True)
    kills = db.Column(db.Integer, nullable=False, default=0)
    kill_reward_cash = db.Column(db.Integer, nullable=False, default=0)
    earned_cash = db.Column(db.Integer, nullable=False, default=0)
    settled_at = db.Column(db.DateTime, nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)
    updated_at = db.Column(db.DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)

    match = db.relationship("ExtractionMatch", back_populates="participants")
    user = db.relationship("User")
    class_rule = db.relationship("ExtractionClassRule")
    weapon_rule = db.relationship("ExtractionWeaponRule")
    weapon_inventory_item = db.relationship("ExtractionInventoryItem", foreign_keys=[weapon_inventory_item_id])
    __table_args__ = (db.UniqueConstraint("match_id", "user_id", name="uq_extraction_match_user"),)

class ExtractionUiSetting(db.Model):
    __tablename__ = "extraction_ui_settings"
    id = db.Column(db.Integer, primary_key=True)
    banner_filename = db.Column(db.String(255), nullable=True)
    updated_at = db.Column(db.DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)



class AttendanceEvent(db.Model):
    __tablename__ = "attendance_events"
    id = db.Column(db.Integer, primary_key=True)
    source_activity_id = db.Column(db.Integer, db.ForeignKey("activities.id"), nullable=True, unique=True, index=True)
    name = db.Column(db.String(160), nullable=False)
    event_date = db.Column(db.Date, nullable=False, index=True)
    location = db.Column(db.String(255), nullable=True)
    organizer = db.Column(db.String(120), nullable=True)
    activity_region = db.Column(db.String(20), default="宁波", nullable=False)
    is_manual = db.Column(db.Boolean, default=False, nullable=False)
    created_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)

    source_activity = db.relationship("Activity")
    created_by = db.relationship("User")
    records = db.relationship("AttendanceRecord", back_populates="event", cascade="all, delete-orphan")


class AttendanceRecord(db.Model):
    __tablename__ = "attendance_records"
    id = db.Column(db.Integer, primary_key=True)
    event_id = db.Column(db.Integer, db.ForeignKey("attendance_events.id"), nullable=False, index=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    present = db.Column(db.Boolean, default=True, nullable=False)
    updated_by_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    updated_at = db.Column(db.DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)

    event = db.relationship("AttendanceEvent", back_populates="records")
    user = db.relationship("User", foreign_keys=[user_id])
    updated_by = db.relationship("User", foreign_keys=[updated_by_id])
    __table_args__ = (db.UniqueConstraint("event_id", "user_id", name="uq_attendance_event_user"),)


class Enrollment(db.Model):
    __tablename__ = "enrollments"
    id = db.Column(db.Integer, primary_key=True)
    activity_id = db.Column(db.Integer, db.ForeignKey("activities.id"), nullable=False, index=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    rent_launcher = db.Column(db.Boolean, default=False, nullable=False)
    camp_no = db.Column(db.Integer, nullable=True)
    squad_no = db.Column(db.Integer, nullable=True)
    job = db.Column(db.String(30), nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.now, nullable=False)
    updated_at = db.Column(db.DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)

    activity = db.relationship("Activity", back_populates="enrollments")
    user = db.relationship("User")
    __table_args__ = (db.UniqueConstraint("activity_id", "user_id", name="uq_activity_user"),)


@login_manager.user_loader
def load_user(user_id):
    return db.session.get(User, int(user_id))


@app.before_request
def update_last_seen():
    if current_user.is_authenticated:
        current_user.last_seen = datetime.now()
        db.session.commit()


@app.context_processor
def inject_globals():
    is_manager = False
    can_view_attendance_nav = False
    if current_user.is_authenticated:
        is_manager = bool(getattr(current_user, "is_admin", False))
        can_view_attendance_nav = True
    can_access_extraction_nav = bool(getattr(current_user, "can_access_extraction", False))
    return {"APP_TITLE": APP_TITLE, "JOB_OPTIONS": JOB_OPTIONS, "ACTIVITY_REGION_OPTIONS": ACTIVITY_REGION_OPTIONS, "ACTIVITY_VISIBILITY_OPTIONS": ACTIVITY_VISIBILITY_OPTIONS, "is_manager": is_manager, "can_view_attendance_nav": can_view_attendance_nav, "can_access_extraction_nav": can_access_extraction_nav, "format_date_with_weekday": format_date_with_weekday, "format_plan_date_option": format_plan_date_option}


def admin_required(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        if not current_user.is_authenticated or not current_user.is_admin:
            flash("需要管理员权限。", "error")
            return redirect(url_for("dashboard"))
        return func(*args, **kwargs)

    return wrapper


def superadmin_required(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        if not current_user.is_authenticated or not current_user.is_superadmin:
            flash("需要超级管理员权限。", "error")
            return redirect(url_for("users"))
        return func(*args, **kwargs)

    return wrapper




def attendance_manage_required(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        if not current_user.is_authenticated or not current_user.can_manage_attendance:
            flash("需要超级管理员或出勤率管理权限。", "error")
            return redirect(url_for("dashboard"))
        return func(*args, **kwargs)

    return wrapper





def extraction_access_required(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        if not current_user.is_authenticated or not current_user.can_access_extraction:
            flash("需要逃离西撇镇授权。", "error")
            return redirect(url_for("dashboard"))
        return func(*args, **kwargs)

    return wrapper


def extraction_manage_required(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        if not current_user.is_authenticated or not current_user.can_manage_extraction:
            flash("需要逃离西撇镇管理员或超级管理员权限。", "error")
            return redirect(url_for("dashboard"))
        return func(*args, **kwargs)

    return wrapper


def current_extraction_season():
    today = datetime.now().date()
    return ExtractionSeason.query.filter(
        ExtractionSeason.active.is_(True),
        ExtractionSeason.start_date <= today,
        ExtractionSeason.end_date >= today,
    ).order_by(ExtractionSeason.start_date.desc()).first()


def get_extraction_ui_setting() -> ExtractionUiSetting:
    setting = ExtractionUiSetting.query.first()
    if not setting:
        setting = ExtractionUiSetting()
        db.session.add(setting)
        db.session.commit()
    return setting


def deactivate_expired_extraction_seasons() -> None:
    today = datetime.now().date()
    expired = ExtractionSeason.query.filter(ExtractionSeason.active.is_(True), ExtractionSeason.end_date < today).all()
    changed = False
    for season in expired:
        season.active = False
        changed = True
    if changed:
        db.session.commit()


def sync_extraction_run_counts() -> None:
    mode_name = "逃离西撇镇"
    activities = Activity.query.filter(
        Activity.deleted_at.is_(None),
        Activity.end_at < datetime.now(),
        Activity.game_modes.ilike(f"%{mode_name}%")
    ).all()
    for activity in activities:
        if enrollment_count(activity.id) < activity.open_min:
            continue
        for en in Enrollment.query.filter_by(activity_id=activity.id).all():
            if not ExtractionRunRecord.query.filter_by(activity_id=activity.id, user_id=en.user_id).first():
                db.session.add(ExtractionRunRecord(activity_id=activity.id, user_id=en.user_id))
                profile = get_extraction_profile(en.user_id)
                profile.runs_count += 1
    db.session.commit()


def get_extraction_profile(user_id: int) -> ExtractionUserProfile:
    profile = ExtractionUserProfile.query.filter_by(user_id=user_id).first()
    if not profile:
        profile = ExtractionUserProfile(user_id=user_id)
        db.session.add(profile)
        db.session.commit()
    return profile


def extraction_today_price(item_def: ExtractionItemDef) -> int:
    today = datetime.now().date()
    price_row = ExtractionItemPrice.query.filter_by(item_def_id=item_def.id, price_date=today).first()
    if not price_row:
        import random
        low = max(1, int(item_def.min_price or 1))
        high = max(low, int(item_def.max_price or low))
        price_row = ExtractionItemPrice(item_def_id=item_def.id, price_date=today, price=random.randint(low, high))
        db.session.add(price_row)
        db.session.commit()
    return int(price_row.price)


def extraction_yesterday_price(item_def: ExtractionItemDef):
    yesterday = datetime.now().date() - timedelta(days=1)
    row = ExtractionItemPrice.query.filter_by(item_def_id=item_def.id, price_date=yesterday).first()
    return None if not row else int(row.price)


def refresh_extraction_prices() -> None:
    for item_def in ExtractionItemDef.query.filter_by(active=True).all():
        extraction_today_price(item_def)


def extraction_trend(item_def: ExtractionItemDef):
    today = extraction_today_price(item_def)
    yesterday = extraction_yesterday_price(item_def)
    if yesterday is None or today == yesterday:
        return "equal", "="
    if today > yesterday:
        return "up", "▲"
    return "down", "▼"




def ensure_default_extraction_rules() -> None:
    defaults = [("跑刀仔", 100, 0, 1), ("拖鞋军", 120, 50, 2), ("正规军", 150, 100, 3), ("重装兵", 200, 200, 4)]
    changed = False
    for name, health, fee, order in defaults:
        rule = ExtractionClassRule.query.filter_by(name=name).first()
        if not rule:
            db.session.add(ExtractionClassRule(name=name, health=health, maintenance_fee=fee, sort_order=order))
            changed = True
    knife = ExtractionWeaponRule.query.filter_by(weapon_type="knife", name="刀", item_def_id=None).first()
    if not knife:
        db.session.add(ExtractionWeaponRule(weapon_type="knife", name="刀", usage_fee=0, durability_cost_percent=0))
        changed = True
    regular = ExtractionWeaponRule.query.filter_by(weapon_type="regular", name="常规武器", item_def_id=None).first()
    if not regular:
        db.session.add(ExtractionWeaponRule(weapon_type="regular", name="常规武器", usage_fee=50, durability_cost_percent=0))
        changed = True
    db.session.flush()
    for item in ExtractionItemDef.query.filter_by(active=True, item_category="武器").all():
        rule = ExtractionWeaponRule.query.filter_by(weapon_type="special", item_def_id=item.id).first()
        if not rule:
            db.session.add(ExtractionWeaponRule(weapon_type="special", name=item.name, item_def_id=item.id, usage_fee=0, durability_cost_percent=10))
            changed = True
        elif rule.name != item.name:
            rule.name = item.name
            changed = True
    if changed:
        db.session.commit()


def extraction_match_status_label(status: str) -> str:
    return {"preparing": "开局整备", "started": "对局进行中", "ended": "已终局", "cancelled": "已取消"}.get(status, status)


def get_user_weapon_choices(user_id: int):
    ensure_default_extraction_rules()
    base_rules = ExtractionWeaponRule.query.filter(ExtractionWeaponRule.active.is_(True), ExtractionWeaponRule.weapon_type.in_(["knife", "regular"])).order_by(ExtractionWeaponRule.weapon_type.asc()).all()
    choices = []
    for rule in base_rules:
        choices.append({"value": f"rule:{rule.id}", "label": f"{rule.name}（使用费 {rule.usage_fee}）", "rule": rule, "inventory_item": None})
    special_items = ExtractionInventoryItem.query.join(ExtractionItemDef).filter(
        ExtractionInventoryItem.user_id == user_id,
        ExtractionInventoryItem.sold_at.is_(None),
        ExtractionInventoryItem.location == "storage",
        ExtractionItemDef.active.is_(True),
        ExtractionItemDef.item_category == "武器",
    ).order_by(ExtractionItemDef.name.asc(), ExtractionInventoryItem.id.asc()).all()
    for inv in special_items:
        rule = ExtractionWeaponRule.query.filter_by(weapon_type="special", item_def_id=inv.item_def_id, active=True).first()
        if rule:
            choices.append({"value": f"special:{inv.id}", "label": f"特殊武器：{inv.item_def.name}（耐久 {inv.durability_percent}%）", "rule": rule, "inventory_item": inv})
    return choices


def parse_weapon_choice(user_id: int, raw: str):
    ensure_default_extraction_rules()
    raw = (raw or "").strip()
    if raw.startswith("special:"):
        try:
            inv_id = int(raw.split(":", 1)[1])
        except ValueError:
            return None, None
        inv = db.session.get(ExtractionInventoryItem, inv_id)
        if not inv or inv.user_id != user_id or inv.sold_at or inv.location != "storage" or inv.item_def.item_category != "武器":
            return None, None
        rule = ExtractionWeaponRule.query.filter_by(weapon_type="special", item_def_id=inv.item_def_id, active=True).first()
        return rule, inv
    if raw.startswith("rule:"):
        try:
            rule_id = int(raw.split(":", 1)[1])
        except ValueError:
            return None, None
        rule = db.session.get(ExtractionWeaponRule, rule_id)
        if rule and rule.active and rule.weapon_type in {"knife", "regular"}:
            return rule, None
    return None, None


def participant_cost(participant: ExtractionMatchParticipant) -> int:
    total = 0
    if participant.class_rule:
        total += int(participant.class_rule.maintenance_fee or 0)
    if participant.weapon_rule and participant.weapon_rule.weapon_type == "regular":
        total += int(participant.weapon_rule.usage_fee or 0)
    return max(0, total)


def average_item_def_value(item_def: ExtractionItemDef) -> int:
    """用于对局统计：单个物品价值按最高价和最低价的平均数计算。"""
    try:
        low = int(item_def.min_price or 0)
        high = int(item_def.max_price or 0)
    except (TypeError, ValueError):
        return 0
    return max(0, int(round((low + high) / 2)))


def get_user_extraction_match_stats(user_id: int, season: ExtractionSeason | None = None) -> dict:
    """按已结束对局统计个人逃离西撇镇数据。"""
    query = ExtractionMatchParticipant.query.join(ExtractionMatch).filter(
        ExtractionMatchParticipant.user_id == user_id,
        ExtractionMatch.status == "ended",
    )
    if season:
        query = query.filter(ExtractionMatch.ended_at >= datetime.combine(season.start_date, datetime.min.time()),
                             ExtractionMatch.ended_at <= datetime.combine(season.end_date, datetime.max.time()))
    participants = query.all()
    match_count = len(participants)
    evac_count = sum(1 for p in participants if bool(p.evacuated))
    total_cash = sum(int(p.earned_cash or 0) + int(getattr(p, "kill_reward_cash", 0) or 0) for p in participants)
    participant_ids = [p.id for p in participants]
    total_item_value = 0
    if participant_ids:
        reward_items = ExtractionInventoryItem.query.filter(ExtractionInventoryItem.match_participant_id.in_(participant_ids)).all()
        total_item_value = sum(average_item_def_value(it.item_def) for it in reward_items if it.item_def)
    return {
        "match_count": match_count,
        "evac_count": evac_count,
        "evac_rate": (round(evac_count * 100 / match_count, 1) if match_count else 0),
        "avg_cash": (round(total_cash / match_count, 1) if match_count else 0),
        "avg_item_value": (round(total_item_value / match_count, 1) if match_count else 0),
        "total_cash": total_cash,
        "total_item_value": total_item_value,
    }


def find_storage_slot_for_item(user_id: int, item_def: ExtractionItemDef):
    profile = get_extraction_profile(user_id)
    return first_free_slot(user_id, "storage", profile.storage_rows, profile.storage_cols, item_def.width, item_def.height)


def active_extraction_matches_query():
    # 对局管理首页只显示未结束的对局卡片；已终局对局放到“对局记录”页面查看。
    return ExtractionMatch.query.filter(ExtractionMatch.status.in_(["preparing", "started"])).order_by(ExtractionMatch.created_at.desc())

def can_place_extraction_item(user_id: int, location: str, rows: int, cols: int, item_w: int, item_h: int, row: int, col: int, exclude_item_id: int | None = None) -> bool:
    """Check warehouse boundary and collision by occupied grid cells. Rows/cols are 1-based."""
    try:
        row = int(row)
        col = int(col)
    except (TypeError, ValueError):
        return False
    if row < 1 or col < 1 or row + item_h - 1 > rows or col + item_w - 1 > cols:
        return False
    existing = ExtractionInventoryItem.query.filter_by(user_id=user_id, location=location, sold_at=None).all()
    occupied = set()
    for it in existing:
        if exclude_item_id is not None and it.id == exclude_item_id:
            continue
        if it.row is None or it.col is None:
            continue
        for r in range(it.row, it.row + it.item_def.height):
            for c in range(it.col, it.col + it.item_def.width):
                occupied.add((r, c))
    for rr in range(row, row + item_h):
        for cc in range(col, col + item_w):
            if (rr, cc) in occupied:
                return False
    return True


def first_free_slot(user_id: int, location: str, rows: int, cols: int, item_w: int, item_h: int, exclude_item_id: int | None = None):
    for r in range(1, rows - item_h + 2):
        for c in range(1, cols - item_w + 2):
            if can_place_extraction_item(user_id, location, rows, cols, item_w, item_h, r, c, exclude_item_id=exclude_item_id):
                return r, c
    return None, None


def normalize_extraction_inventory_layout(user_id: int, location: str, rows: int, cols: int) -> None:
    """Old data may not have row/col. Assign missing items to the first empty slots so grid display is stable."""
    changed = False
    items = ExtractionInventoryItem.query.filter_by(user_id=user_id, location=location, sold_at=None).order_by(ExtractionInventoryItem.created_at.asc()).all()
    for it in items:
        if it.row is not None and it.col is not None and can_place_extraction_item(user_id, location, rows, cols, it.item_def.width, it.item_def.height, it.row, it.col, exclude_item_id=it.id):
            continue
        row, col = first_free_slot(user_id, location, rows, cols, it.item_def.width, it.item_def.height, exclude_item_id=it.id)
        if row is not None:
            it.row = row
            it.col = col
            changed = True
    if changed:
        db.session.commit()


def sell_extraction_inventory_item(inv_item: ExtractionInventoryItem) -> int:
    if inv_item.sold_at:
        return 0
    profile = get_extraction_profile(inv_item.user_id)
    price = extraction_today_price(inv_item.item_def)
    inv_item.sold_at = datetime.now()
    inv_item.sold_price = price
    profile.cash += price
    db.session.commit()
    return price


def auto_sell_extraction_buffers() -> None:
    now = datetime.now()
    today_5 = datetime.combine(now.date(), time(5, 0))
    cutoff = today_5 if now >= today_5 else today_5 - timedelta(days=1)
    items = ExtractionInventoryItem.query.filter(
        ExtractionInventoryItem.location == "buffer",
        ExtractionInventoryItem.sold_at.is_(None),
        ExtractionInventoryItem.created_at < cutoff,
    ).all()
    for item in items:
        sell_extraction_inventory_item(item)


def sync_extraction_runtime() -> None:
    deactivate_expired_extraction_seasons()
    ensure_default_extraction_rules()
    refresh_extraction_prices()
    auto_sell_extraction_buffers()
    sync_extraction_run_counts()

def can_view_attendance_page() -> bool:
    return current_user.is_authenticated


def sync_attendance_events() -> None:
    """把已经成功结束的活动自动生成出勤记录，并记录活动地区。"""
    now = datetime.now()
    activities = Activity.query.filter(
        Activity.end_at < now,
        Activity.deleted_at.is_(None),
        Activity.attendance_enabled.is_(True),
    ).all()
    changed = False
    for activity in activities:
        if AttendanceEvent.query.filter_by(source_activity_id=activity.id).first():
            continue
        if enrollment_count(activity.id) < activity.open_min:
            continue
        organizer = activity.created_by.callsign if activity.created_by else "-"
        event = AttendanceEvent(
            source_activity_id=activity.id,
            name=activity.name,
            event_date=activity.start_at.date(),
            location=activity.location,
            organizer=organizer,
            activity_region=activity.activity_region or "宁波",
            is_manual=False,
        )
        db.session.add(event)
        db.session.flush()
        for enrollment in Enrollment.query.filter_by(activity_id=activity.id).all():
            db.session.add(AttendanceRecord(event_id=event.id, user_id=enrollment.user_id, present=True))
        changed = True
    if changed:
        db.session.commit()


def set_attendance_record(event_id: int, user_id: int, present: bool) -> None:
    record = AttendanceRecord.query.filter_by(event_id=event_id, user_id=user_id).first()
    if not record:
        record = AttendanceRecord(event_id=event_id, user_id=user_id, present=present, updated_by_id=current_user.id)
        db.session.add(record)
    else:
        record.present = present
        record.updated_by_id = current_user.id
        record.updated_at = datetime.now()

def parse_datetime_local(value: str) -> datetime:
    # HTML datetime-local 默认格式：YYYY-MM-DDTHH:MM
    return datetime.strptime(value, "%Y-%m-%dT%H:%M")


def get_activity_lock_time(activity: Activity) -> datetime:
    # SOW：活动前一天 22:00 后进入锁定状态。
    lock_date = activity.start_at.date() - timedelta(days=1)
    return datetime.combine(lock_date, time(22, 0))


def enrollment_count(activity_id: int) -> int:
    return Enrollment.query.filter_by(activity_id=activity_id).count()


def activity_status(activity: Activity) -> tuple[str, str]:
    """返回状态文字和 CSS class。"""
    if getattr(activity, "deleted_at", None):
        return "已取消", "cancelled"
    now = datetime.now()
    count = enrollment_count(activity.id)
    lock_dt = get_activity_lock_time(activity)

    if now > activity.end_at:
        return "活动结束", "ended"

    if activity.start_at <= now <= activity.end_at:
        if count >= activity.open_min:
            return "活动进行中", "running"
        return "活动取消", "cancelled"

    if now >= lock_dt:
        return "已锁定", "locked"
    return "报名中", "signup"



def parse_invitee_ids(value: str | None) -> set[int]:
    result = set()
    for part in (value or "").split(","):
        part = part.strip()
        if not part:
            continue
        try:
            result.add(int(part))
        except ValueError:
            continue
    return result


def get_activity_invited_users(activity: Activity):
    ids = parse_invitee_ids(activity.invitee_ids)
    if not ids:
        return []
    return User.query.filter(User.id.in_(ids)).order_by(User.callsign.asc()).all()


def activity_visibility_label(activity: Activity) -> str:
    return ACTIVITY_VISIBILITY_OPTIONS.get(activity.visibility_type or "all", "所有人可见")


def can_view_activity(activity: Activity) -> bool:
    if not current_user.is_authenticated:
        return False
    if getattr(activity, "deleted_at", None):
        return bool(current_user.is_admin)
    if current_user.is_admin:
        return True
    visibility = activity.visibility_type or "all"
    if visibility == "all":
        return True
    if visibility == "official_only":
        return bool(current_user.is_regular_member)
    if visibility == "official_plus_invite":
        return bool(current_user.is_regular_member) or current_user.id in parse_invitee_ids(activity.invitee_ids)
    return True


def can_view_activity_visibility_info(activity: Activity) -> bool:
    # 可见范围/邀请名单信息只给管理员和正式队员看；被邀请的普通用户可以看活动，但不显示这些设置。
    return current_user.is_authenticated and (current_user.is_admin or bool(current_user.is_regular_member))


def can_view_plan(plan: ActivityPlan) -> bool:
    if not current_user.is_authenticated:
        return False
    if current_user.is_admin:
        return True
    visibility = getattr(plan, "visibility_type", "all") or "all"
    if visibility == "all":
        return True
    if visibility == "official_only":
        return bool(current_user.is_regular_member)
    if visibility == "official_plus_invite":
        return bool(current_user.is_regular_member) or current_user.id in parse_invitee_ids(getattr(plan, "invitee_ids", ""))
    return True


def plan_visibility_label(plan: ActivityPlan) -> str:
    return ACTIVITY_VISIBILITY_OPTIONS.get(getattr(plan, "visibility_type", "all") or "all", "所有人可见")


def get_plan_invited_users(plan: ActivityPlan):
    ids = parse_invitee_ids(getattr(plan, "invitee_ids", ""))
    if not ids:
        return []
    return User.query.filter(User.id.in_(ids)).order_by(User.callsign.asc()).all()


def format_date_with_weekday(value):
    if not value:
        return "-"
    weekdays = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
    try:
        return f"{value.strftime('%Y-%m-%d')}（{weekdays[value.weekday()]}）"
    except Exception:
        return str(value)


def format_plan_date_option(option):
    """Format a planning date option with weekday and optional remark."""
    if not option:
        return "-"
    base = format_date_with_weekday(option.date)
    note = (getattr(option, "note", None) or "").strip()
    return f"{base}｜{note}" if note else base


def parse_activity_visibility_from_form():
    visibility_type = request.form.get("visibility_type", "all").strip() or "all"
    if visibility_type not in ACTIVITY_VISIBILITY_OPTIONS:
        raise ValueError("请选择有效的活动可见范围。")
    invitee_ids = []
    if visibility_type == "official_plus_invite":
        valid_ids = {u.id for u in User.query.filter(User.role == "user").all()}
        for raw in request.form.getlist("invitee_ids"):
            try:
                uid = int(raw)
            except (TypeError, ValueError):
                continue
            if uid in valid_ids:
                invitee_ids.append(uid)
    return visibility_type, ",".join(str(uid) for uid in sorted(set(invitee_ids)))

def can_user_join(activity: Activity) -> bool:
    status, _ = activity_status(activity)
    return can_view_activity(activity) and status in {"报名中", "已锁定"}


def can_self_adjust(activity: Activity) -> bool:
    status, _ = activity_status(activity)
    return status == "报名中" and not activity.list_locked


def camp_member_count(activity_id: int, camp_no: int, exclude_enrollment_id: int | None = None) -> int:
    query = Enrollment.query.filter_by(activity_id=activity_id, camp_no=camp_no)
    if exclude_enrollment_id:
        query = query.filter(Enrollment.id != exclude_enrollment_id)
    return query.count()


def squad_member_count(activity_id: int, camp_no: int, squad_no: int, exclude_enrollment_id: int | None = None) -> int:
    # 小队人数按“阵营 + 小队”计算：阵营 1 的小队 1 与阵营 2 的小队 1 是两个不同小队。
    query = Enrollment.query.filter_by(activity_id=activity_id, camp_no=camp_no, squad_no=squad_no)
    if exclude_enrollment_id:
        query = query.filter(Enrollment.id != exclude_enrollment_id)
    return query.count()


def default_squad_name(squad_no: int) -> str:
    if 1 <= squad_no <= 26:
        return f"{chr(64 + squad_no)}队"
    return f"小队 {squad_no}"


def default_radio_channel(camp_no: int, squad_no: int) -> str:
    # 默认 435.000、435.100 依次类推；不同阵营继续顺延，避免默认重复。
    offset = (camp_no - 1) * 10 + (squad_no - 1)
    return f"{435.0 + offset * 0.1:.3f}"


def get_or_create_camp_setting(activity_id: int, camp_no: int) -> CampSetting:
    setting = CampSetting.query.filter_by(activity_id=activity_id, camp_no=camp_no).first()
    if not setting:
        setting = CampSetting(activity_id=activity_id, camp_no=camp_no)
        db.session.add(setting)
    return setting


def get_or_create_squad_setting(activity_id: int, camp_no: int, squad_no: int) -> SquadSetting:
    setting = SquadSetting.query.filter_by(activity_id=activity_id, camp_no=camp_no, squad_no=squad_no).first()
    if not setting:
        setting = SquadSetting(
            activity_id=activity_id,
            camp_no=camp_no,
            squad_no=squad_no,
            name=default_squad_name(squad_no),
            radio_channel=default_radio_channel(camp_no, squad_no),
        )
        db.session.add(setting)
    return setting


def ensure_activity_settings(activity: Activity) -> None:
    changed = False
    for camp in range(1, activity.camp_count + 1):
        if not CampSetting.query.filter_by(activity_id=activity.id, camp_no=camp).first():
            db.session.add(CampSetting(activity_id=activity.id, camp_no=camp))
            changed = True
        for squad in range(1, activity.squad_count + 1):
            if not SquadSetting.query.filter_by(activity_id=activity.id, camp_no=camp, squad_no=squad).first():
                db.session.add(SquadSetting(
                    activity_id=activity.id,
                    camp_no=camp,
                    squad_no=squad,
                    name=default_squad_name(squad),
                    radio_channel=default_radio_channel(camp, squad),
                ))
                changed = True
    if changed:
        db.session.flush()


def assign_default_leader_if_needed(activity: Activity, camp_no: int | None, squad_no: int | None, user_id: int) -> None:
    if not camp_no or not squad_no:
        return
    setting = get_or_create_squad_setting(activity.id, camp_no, squad_no)
    if not setting.leader_user_id:
        setting.leader_user_id = user_id
        setting.updated_at = datetime.now()


def reassign_leader_if_needed(activity: Activity, camp_no: int | None, squad_no: int | None) -> None:
    if not camp_no or not squad_no:
        return
    setting = SquadSetting.query.filter_by(activity_id=activity.id, camp_no=camp_no, squad_no=squad_no).first()
    if not setting or not setting.leader_user_id:
        return
    still_here = Enrollment.query.filter_by(
        activity_id=activity.id,
        user_id=setting.leader_user_id,
        camp_no=camp_no,
        squad_no=squad_no,
    ).first()
    if still_here:
        return
    next_member = (
        Enrollment.query.filter_by(activity_id=activity.id, camp_no=camp_no, squad_no=squad_no)
        .order_by(Enrollment.created_at.asc(), Enrollment.id.asc())
        .first()
    )
    setting.leader_user_id = next_member.user_id if next_member else None
    setting.updated_at = datetime.now()


def can_user_cancel(activity: Activity) -> bool:
    status, _ = activity_status(activity)
    return status not in {"活动结束", "活动取消"}


def is_squad_leader(activity_id: int, camp_no: int, squad_no: int, user_id: int) -> bool:
    setting = SquadSetting.query.filter_by(activity_id=activity_id, camp_no=camp_no, squad_no=squad_no).first()
    return bool(setting and setting.leader_user_id == user_id)


def is_squad_locked(activity_id: int, camp_no: int | None, squad_no: int | None) -> bool:
    if not camp_no or not squad_no:
        return False
    setting = SquadSetting.query.filter_by(activity_id=activity_id, camp_no=camp_no, squad_no=squad_no).first()
    return bool(setting and setting.locked)


def _normalize_assignment_number(value, max_value: int, label: str):
    if value is None:
        return None
    if value == "":
        return None
    value = int(value)
    if value < 1 or value > max_value:
        raise ValueError(f"{label}不存在。")
    return value


def validate_assignment(activity: Activity, enrollment: Enrollment, camp_no=None, squad_no=None, job=None):
    new_camp = _normalize_assignment_number(camp_no, activity.camp_count, "阵营") if camp_no is not None else None
    new_squad = _normalize_assignment_number(squad_no, activity.squad_count, "小队") if squad_no is not None else None

    effective_camp = enrollment.camp_no if camp_no is None else new_camp
    effective_squad = enrollment.squad_no if squad_no is None else new_squad

    # 小队锁定后，普通用户不能再加入该小队；管理员仍可进行后台分配。
    assignment_changed = (camp_no is not None or squad_no is not None)
    moving_into_target = (effective_camp != enrollment.camp_no or effective_squad != enrollment.squad_no)
    if assignment_changed and moving_into_target and not current_user.is_admin:
        if is_squad_locked(activity.id, effective_camp, effective_squad):
            raise ValueError("该小队已锁定，不能再加入。")

    if effective_camp is not None:
        if camp_member_count(activity.id, effective_camp, enrollment.id) >= activity.camp_limit:
            raise ValueError("该阵营人数已满，请选择其他阵营。")

    if effective_squad is not None:
        if effective_camp is None:
            raise ValueError("请选择小队前需要先选择阵营。")
        if squad_member_count(activity.id, effective_camp, effective_squad, enrollment.id) >= activity.squad_limit:
            raise ValueError("该小队人数已满，请选择其他小队。")

    if job is not None:
        if job == "":
            job = None
        elif job not in activity.allowed_job_list:
            raise ValueError("该职业未在本活动开放。")

    return new_camp, new_squad, job


def parse_date_list(raw: str):
    dates = []
    seen = set()
    for part in (raw or "").replace("，", ",").replace(";", ",").replace("；", ",").split(","):
        value = part.strip()
        if not value:
            continue
        d = datetime.strptime(value, "%Y-%m-%d").date()
        if d not in seen:
            dates.append(d)
            seen.add(d)
    return dates


def plan_status(plan: ActivityPlan) -> tuple[str, str]:
    if plan.hidden:
        return "已隐藏", "ended"
    if datetime.now() > plan.vote_deadline:
        return "投票结束", "locked"
    return "策划中", "planning"


def plan_vote_stats(plan: ActivityPlan):
    stats = {"date": {}, "venue": {}, "mode": {}}
    for v in plan.votes:
        stats.setdefault(v.option_type, {})[v.option_id] = stats.setdefault(v.option_type, {}).get(v.option_id, 0) + 1
    return stats





def get_launcher_rental_setting() -> LauncherRentalSetting:
    setting = db.session.get(LauncherRentalSetting, 1)
    if not setting:
        setting = LauncherRentalSetting(id=1, note="")
        db.session.add(setting)
        db.session.flush()
    return setting


PHOTO_EXTENSIONS = {"jpg", "jpeg", "png", "gif", "webp"}


def uploaded_photo_extension(file_storage) -> str:
    """Return a normalized extension after checking the uploaded image bytes."""
    original_name = os.path.basename((file_storage.filename or "").strip())
    ext = os.path.splitext(original_name)[1].lstrip(".").lower()
    if ext not in PHOTO_EXTENSIONS:
        raise ValueError("照片格式只支持 jpg、jpeg、png、gif、webp。")

    header = file_storage.stream.read(16)
    file_storage.stream.seek(0)
    signatures = {
        "jpg": header.startswith(b"\xff\xd8\xff"),
        "jpeg": header.startswith(b"\xff\xd8\xff"),
        "png": header.startswith(b"\x89PNG\r\n\x1a\n"),
        "gif": header.startswith((b"GIF87a", b"GIF89a")),
        "webp": header.startswith(b"RIFF") and header[8:12] == b"WEBP",
    }
    if not signatures[ext]:
        raise ValueError("图片内容与文件扩展名不一致，请重新选择图片。")
    return "jpg" if ext == "jpeg" else ext


def save_uploaded_photo(file_storage, upload_subdir: str, stable_prefix: str):
    """Save uploaded image with a stable deterministic filename.

    The database stores this filename, and the file is kept under static/uploads.
    Stable names prevent image links from changing between Docker redeployments as long
    as static/uploads and data/app.db are kept together.
    """
    if not file_storage or not file_storage.filename:
        return None
    ext = uploaded_photo_extension(file_storage)
    upload_dir = os.path.join(BASE_DIR, "static", "uploads", upload_subdir)
    os.makedirs(upload_dir, exist_ok=True)
    final_name = f"{stable_prefix}.{ext}"

    # Remove old files with the same stable prefix and other extensions to avoid stale references.
    for old_ext in ("jpg", "jpeg", "png", "gif", "webp"):
        old_path = os.path.join(upload_dir, f"{stable_prefix}.{old_ext}")
        if old_path != os.path.join(upload_dir, final_name) and os.path.exists(old_path):
            try:
                os.remove(old_path)
            except OSError:
                pass
    file_storage.save(os.path.join(upload_dir, final_name))
    return final_name


def save_launcher_photo(file_storage, launcher_id=None):
    if launcher_id is None:
        stable_prefix = f"launcher_tmp_{datetime.now().strftime('%Y%m%d%H%M%S')}_{secrets.token_hex(4)}"
    else:
        stable_prefix = f"launcher_{launcher_id}"
    return save_uploaded_photo(file_storage, "launchers", stable_prefix)


def active_launcher_rentals_query(now=None):
    now = now or datetime.now()
    return (
        ActivityLauncherRental.query
        .join(Activity, ActivityLauncherRental.activity_id == Activity.id)
        .filter(ActivityLauncherRental.cancelled_at.is_(None), Activity.end_at > now)
    )


def unavailable_launcher_ids(now=None):
    return {row.launcher_id for row in active_launcher_rentals_query(now).all()}


def is_launcher_available(launcher_id: int, now=None) -> bool:
    now = now or datetime.now()
    return not active_launcher_rentals_query(now).filter(ActivityLauncherRental.launcher_id == launcher_id).first()


def activity_launcher_rental_rows(activity_id: int):
    return (
        ActivityLauncherRental.query
        .filter_by(activity_id=activity_id, cancelled_at=None)
        .join(User, ActivityLauncherRental.user_id == User.id)
        .join(LauncherRentalItem, ActivityLauncherRental.launcher_id == LauncherRentalItem.id)
        .order_by(ActivityLauncherRental.rented_at.asc(), ActivityLauncherRental.id.asc())
        .all()
    )


def activity_allowed_launcher_ids(activity_id: int):
    return {row.launcher_id for row in ActivityLauncherOption.query.filter_by(activity_id=activity_id).all()}


def activity_available_launchers(activity_id: int):
    ids = activity_allowed_launcher_ids(activity_id)
    if not ids:
        return []
    return (
        LauncherRentalItem.query
        .filter(LauncherRentalItem.active.is_(True), LauncherRentalItem.id.in_(ids))
        .order_by(LauncherRentalItem.name.asc())
        .all()
    )


def replace_activity_launcher_options(activity_id: int, launcher_ids):
    ActivityLauncherOption.query.filter_by(activity_id=activity_id).delete(synchronize_session=False)
    seen = set()
    for raw_id in launcher_ids:
        try:
            launcher_id = int(raw_id)
        except (TypeError, ValueError):
            continue
        if launcher_id in seen:
            continue
        launcher = db.session.get(LauncherRentalItem, launcher_id)
        if launcher and launcher.active:
            db.session.add(ActivityLauncherOption(activity_id=activity_id, launcher_id=launcher_id))
            seen.add(launcher_id)

def plan_vote_details(plan: ActivityPlan):
    """Return per-user vote details for display on the plan page."""
    date_map = {o.id: format_plan_date_option(o) for o in plan.date_options}
    venue_map = {o.venue_id: o.venue.name for o in plan.venue_options}
    rows = {}
    votes = (
        PlanVote.query
        .filter_by(plan_id=plan.id)
        .join(User, PlanVote.user_id == User.id)
        .order_by(User.callsign.asc(), PlanVote.created_at.asc())
        .all()
    )
    for vote in votes:
        row = rows.setdefault(vote.user_id, {
            "callsign": vote.user.callsign if vote.user else f"用户{vote.user_id}",
            "dates": [],
            "venues": [],
        })
        if vote.option_type == "date":
            value = date_map.get(vote.option_id)
            if value and value not in row["dates"]:
                row["dates"].append(value)
        elif vote.option_type == "venue":
            value = venue_map.get(vote.option_id)
            if value and value not in row["venues"]:
                row["venues"].append(value)
    return list(rows.values())

def user_plan_votes(plan_id: int, user_id: int):
    votes = PlanVote.query.filter_by(plan_id=plan_id, user_id=user_id).all()
    return {"date": {v.option_id for v in votes if v.option_type == "date"},
            "venue": {v.option_id for v in votes if v.option_type == "venue"},
            "mode": {v.option_id for v in votes if v.option_type == "mode"}}


def generate_invite_code() -> str:
    alphabet = string.ascii_uppercase + string.digits
    while True:
        raw = "YS-" + "".join(secrets.choice(alphabet) for _ in range(8))
        if not InviteCode.query.filter_by(code=raw).first():
            return raw


def ensure_schema_compatibility():
    """为已有 SQLite 数据库补齐新版本字段，避免升级时必须删除旧数据。"""
    with db.engine.begin() as conn:
        user_cols = {row[1] for row in conn.exec_driver_sql("PRAGMA table_info(users)").fetchall()}
        if "disabled" not in user_cols:
            conn.exec_driver_sql("ALTER TABLE users ADD COLUMN disabled BOOLEAN NOT NULL DEFAULT 0")
        if "is_regular_member" not in user_cols:
            conn.exec_driver_sql("ALTER TABLE users ADD COLUMN is_regular_member BOOLEAN NOT NULL DEFAULT 0")
        if "attendance_manager" not in user_cols:
            conn.exec_driver_sql("ALTER TABLE users ADD COLUMN attendance_manager BOOLEAN NOT NULL DEFAULT 0")
        if "extraction_authorized" not in user_cols:
            conn.exec_driver_sql("ALTER TABLE users ADD COLUMN extraction_authorized BOOLEAN NOT NULL DEFAULT 0")
        if "extraction_manager" not in user_cols:
            conn.exec_driver_sql("ALTER TABLE users ADD COLUMN extraction_manager BOOLEAN NOT NULL DEFAULT 0")
        if "invited_by_id" not in user_cols:
            conn.exec_driver_sql("ALTER TABLE users ADD COLUMN invited_by_id INTEGER")
        if "invite_code_id" not in user_cols:
            conn.exec_driver_sql("ALTER TABLE users ADD COLUMN invite_code_id INTEGER")

        activity_cols = {row[1] for row in conn.exec_driver_sql("PRAGMA table_info(activities)").fetchall()}
        if "game_modes" not in activity_cols:
            conn.exec_driver_sql("ALTER TABLE activities ADD COLUMN game_modes TEXT")
        if "attendance_enabled" not in activity_cols:
            conn.exec_driver_sql("ALTER TABLE activities ADD COLUMN attendance_enabled BOOLEAN NOT NULL DEFAULT 0")
        if "activity_region" not in activity_cols:
            conn.exec_driver_sql("ALTER TABLE activities ADD COLUMN activity_region VARCHAR(20) NOT NULL DEFAULT '宁波'")
        if "visibility_type" not in activity_cols:
            conn.exec_driver_sql("ALTER TABLE activities ADD COLUMN visibility_type VARCHAR(32) NOT NULL DEFAULT 'all'")
        if "invitee_ids" not in activity_cols:
            conn.exec_driver_sql("ALTER TABLE activities ADD COLUMN invitee_ids TEXT")
        if "deleted_at" not in activity_cols:
            conn.exec_driver_sql("ALTER TABLE activities ADD COLUMN deleted_at DATETIME")
        if "deleted_by_id" not in activity_cols:
            conn.exec_driver_sql("ALTER TABLE activities ADD COLUMN deleted_by_id INTEGER")

        plan_cols = {row[1] for row in conn.exec_driver_sql("PRAGMA table_info(activity_plans)").fetchall()}
        if plan_cols and "visibility_type" not in plan_cols:
            conn.exec_driver_sql("ALTER TABLE activity_plans ADD COLUMN visibility_type VARCHAR(32) NOT NULL DEFAULT 'all'")
        if plan_cols and "invitee_ids" not in plan_cols:
            conn.exec_driver_sql("ALTER TABLE activity_plans ADD COLUMN invitee_ids TEXT")

        # V73：活动策划可选日期增加备注。
        plan_date_cols = {row[1] for row in conn.exec_driver_sql("PRAGMA table_info(plan_date_options)").fetchall()}
        if plan_date_cols and "note" not in plan_date_cols:
            conn.exec_driver_sql("ALTER TABLE plan_date_options ADD COLUMN note VARCHAR(80)")

        attendance_event_cols = {row[1] for row in conn.exec_driver_sql("PRAGMA table_info(attendance_events)").fetchall()}
        if attendance_event_cols and "activity_region" not in attendance_event_cols:
            conn.exec_driver_sql("ALTER TABLE attendance_events ADD COLUMN activity_region VARCHAR(20) NOT NULL DEFAULT '宁波'")

        invite_cols = {row[1] for row in conn.exec_driver_sql("PRAGMA table_info(invite_codes)").fetchall()}
        if "max_uses" not in invite_cols:
            conn.exec_driver_sql("ALTER TABLE invite_codes ADD COLUMN max_uses INTEGER NOT NULL DEFAULT 1")
        if "used_count" not in invite_cols:
            conn.exec_driver_sql("ALTER TABLE invite_codes ADD COLUMN used_count INTEGER NOT NULL DEFAULT 0")
            conn.exec_driver_sql("UPDATE invite_codes SET used_count = CASE WHEN used_at IS NULL THEN 0 ELSE 1 END")
        # V71：尽可能从邀请码最近使用记录回填邀请人信息。多次使用邀请码的历史用户无法完全追溯，后续注册会准确记录。
        if {"invited_by_id", "invite_code_id"}.issubset(user_cols | {"invited_by_id", "invite_code_id"}):
            conn.exec_driver_sql("""
                UPDATE users
                SET invited_by_id = (SELECT created_by_id FROM invite_codes WHERE invite_codes.used_by_id = users.id LIMIT 1),
                    invite_code_id = (SELECT id FROM invite_codes WHERE invite_codes.used_by_id = users.id LIMIT 1)
                WHERE invited_by_id IS NULL
                  AND EXISTS (SELECT 1 FROM invite_codes WHERE invite_codes.used_by_id = users.id)
            """)

        # V23：为已有小队配置补齐 locked 字段，支持管理员锁定小队。
        squad_cols = {row[1] for row in conn.exec_driver_sql("PRAGMA table_info(squad_settings)").fetchall()}
        if squad_cols and "locked" not in squad_cols:
            conn.exec_driver_sql("ALTER TABLE squad_settings ADD COLUMN locked BOOLEAN NOT NULL DEFAULT 0")

        # V25：发射器增加 50 字说明字段，历史 owner_type 字段界面改为“发射器所有人”。
        launcher_cols = {row[1] for row in conn.exec_driver_sql("PRAGMA table_info(launcher_rental_items)").fetchall()}
        if launcher_cols and "description" not in launcher_cols:
            conn.exec_driver_sql("ALTER TABLE launcher_rental_items ADD COLUMN description VARCHAR(50)")

        # V56：逃离西撇镇物品增加分类：常规物品 / 武器。
        extraction_item_cols = {row[1] for row in conn.exec_driver_sql("PRAGMA table_info(extraction_item_defs)").fetchall()}
        if extraction_item_cols and "item_category" not in extraction_item_cols:
            conn.exec_driver_sql("ALTER TABLE extraction_item_defs ADD COLUMN item_category VARCHAR(20) NOT NULL DEFAULT '常规物品'")

        # V66：物品等级更新。旧等级自动迁移到新等级，避免旧数据颜色失效。
        if extraction_item_cols and "level" in extraction_item_cols:
            conn.exec_driver_sql("UPDATE extraction_item_defs SET level='超凡' WHERE level='传奇'")
            conn.exec_driver_sql("UPDATE extraction_item_defs SET level='精品' WHERE level='稀有'")
            conn.exec_driver_sql("UPDATE extraction_item_defs SET level='普通' WHERE level='特定'")

        extraction_inv_cols = {row[1] for row in conn.exec_driver_sql("PRAGMA table_info(extraction_inventory_items)").fetchall()}
        if extraction_inv_cols and "durability_percent" not in extraction_inv_cols:
            conn.exec_driver_sql("ALTER TABLE extraction_inventory_items ADD COLUMN durability_percent INTEGER NOT NULL DEFAULT 100")
        if extraction_inv_cols and "match_participant_id" not in extraction_inv_cols:
            conn.exec_driver_sql("ALTER TABLE extraction_inventory_items ADD COLUMN match_participant_id INTEGER")

        extraction_season_cols = {row[1] for row in conn.exec_driver_sql("PRAGMA table_info(extraction_seasons)").fetchall()}
        if extraction_season_cols and "kill_reward_cash" not in extraction_season_cols:
            conn.exec_driver_sql("ALTER TABLE extraction_seasons ADD COLUMN kill_reward_cash INTEGER NOT NULL DEFAULT 0")

        extraction_participant_cols = {row[1] for row in conn.exec_driver_sql("PRAGMA table_info(extraction_match_participants)").fetchall()}
        if extraction_participant_cols and "kill_reward_cash" not in extraction_participant_cols:
            conn.exec_driver_sql("ALTER TABLE extraction_match_participants ADD COLUMN kill_reward_cash INTEGER NOT NULL DEFAULT 0")


    # 为已有活动补齐默认阵营/小队配置。新表由 db.create_all 自动创建。
    try:
        for activity in Activity.query.all():
            ensure_activity_settings(activity)
        db.session.commit()
    except Exception:
        db.session.rollback()


def init_db():
    with app.app_context():
        db.create_all()
        ensure_schema_compatibility()
        username = os.environ.get("SUPERADMIN_USERNAME", "admin")
        password = os.environ.get("SUPERADMIN_PASSWORD", "admin123456")
        callsign = os.environ.get("SUPERADMIN_CALLSIGN", "超级管理员")

        user = User.query.filter_by(username=username).first()
        if not user:
            user = User(username=username, callsign=callsign, role="superadmin")
            user.set_password(password)
            db.session.add(user)
            db.session.commit()
        elif user.role != "superadmin":
            user.role = "superadmin"
            db.session.commit()
        # V60：将旧名称“甬城大逃杀 / 搜打撤”统一迁移为“逃离西撇镇”。
        old_modes = GameMode.query.filter(GameMode.name.in_(["甬城大逃杀", "搜打撤", "大逃杀"])).all()
        target_mode = GameMode.query.filter_by(name="逃离西撇镇").first()
        if target_mode:
            for old_mode in old_modes:
                if old_mode.id != target_mode.id:
                    db.session.delete(old_mode)
        else:
            if old_modes:
                target_mode = old_modes[0]
                target_mode.name = "逃离西撇镇"
                target_mode.rules = target_mode.rules.replace("甬城大逃杀", "逃离西撇镇").replace("搜打撤", "逃离西撇镇").replace("大逃杀", "逃离西撇镇") if target_mode.rules else "逃离西撇镇模式默认预设。"
                for old_mode in old_modes[1:]:
                    db.session.delete(old_mode)
            else:
                target_mode = GameMode(name="逃离西撇镇", suitable_people="不限", rules="逃离西撇镇模式默认预设。", created_by_id=None)
                db.session.add(target_mode)
        # 历史活动中已经选择过旧模式名的，也同步替换。
        for activity in Activity.query.filter(Activity.game_modes.isnot(None)).all():
            gm = activity.game_modes or ""
            new_gm = gm.replace("甬城大逃杀", "逃离西撇镇").replace("搜打撤", "逃离西撇镇").replace("大逃杀", "逃离西撇镇")
            if new_gm != gm:
                activity.game_modes = new_gm
        db.session.commit()
        ensure_default_extraction_rules()


@app.route("/")
def index():
    if current_user.is_authenticated:
        return redirect(url_for("dashboard"))
    return redirect(url_for("login"))


@app.route("/login", methods=["GET", "POST"])
def login():
    if current_user.is_authenticated:
        return redirect(url_for("dashboard"))
    if request.method == "POST":
        username = request.form.get("username", "").strip()
        password = request.form.get("password", "")
        user = User.query.filter_by(username=username).first()
        if user and user.check_password(password):
            if user.disabled:
                flash("该账号已被禁用，请联系管理员。", "error")
                return render_template("login.html")
            login_user(user)
            return redirect(url_for("dashboard"))
        flash("用户名或密码错误。", "error")
    return render_template("login.html")


@app.route("/register", methods=["GET", "POST"])
def register():
    if current_user.is_authenticated:
        return redirect(url_for("dashboard"))
    if request.method == "POST":
        username = request.form.get("username", "").strip()
        callsign = request.form.get("callsign", "").strip()
        password = request.form.get("password", "")
        password2 = request.form.get("password2", "")
        invite_code = request.form.get("invite_code", "").strip().upper()

        if not username or not callsign or not password or not invite_code:
            flash("请完整填写注册信息。", "error")
        elif password != password2:
            flash("两次输入的密码不一致。", "error")
        elif len(password) < 6:
            flash("密码长度至少 6 位。", "error")
        elif User.query.filter_by(username=username).first():
            flash("用户名已存在。", "error")
        else:
            invite = InviteCode.query.filter_by(code=invite_code).first()
            if not invite or invite.is_used:
                flash("邀请码无效或使用次数已用完。", "error")
            else:
                user = User(username=username, callsign=callsign, role=invite.role, invited_by_id=invite.created_by_id, invite_code_id=invite.id)
                user.set_password(password)
                db.session.add(user)
                db.session.flush()
                invite.used_by_id = user.id
                invite.used_at = datetime.now()
                invite.used_count = (invite.used_count or 0) + 1
                db.session.commit()
                flash("注册成功，请登录。", "success")
                return redirect(url_for("login"))
    return render_template("register.html")


@app.route("/logout")
@login_required
def logout():
    logout_user()
    return redirect(url_for("login"))


@app.route("/settings", methods=["GET", "POST"])
@login_required
def settings():
    if request.method == "POST":
        callsign = request.form.get("callsign", "").strip()
        current_password = request.form.get("current_password", "")
        new_password = request.form.get("new_password", "")
        new_password2 = request.form.get("new_password2", "")

        if not callsign:
            flash("呼号不能为空。", "error")
            return redirect(url_for("settings"))

        if new_password or new_password2:
            if not current_password:
                flash("修改密码时需要填写当前密码。", "error")
                return redirect(url_for("settings"))
            if not current_user.check_password(current_password):
                flash("当前密码不正确。", "error")
                return redirect(url_for("settings"))
            if new_password != new_password2:
                flash("两次输入的新密码不一致。", "error")
                return redirect(url_for("settings"))
            if len(new_password) < 6:
                flash("新密码长度至少 6 位。", "error")
                return redirect(url_for("settings"))
            current_user.set_password(new_password)

        current_user.callsign = callsign
        db.session.commit()
        flash("用户设置已保存。", "success")
        return redirect(url_for("settings"))

    return render_template("settings.html")


@app.route("/dashboard")
@login_required
def dashboard():
    try:
        sync_attendance_events()
    except Exception:
        db.session.rollback()
    users_count = User.query.count()
    online_count = User.query.filter(User.last_seen >= datetime.now() - timedelta(minutes=5)).count()
    activities = Activity.query.filter(Activity.deleted_at.is_(None)).order_by(Activity.start_at.desc()).all()
    activity_cards = []
    for activity in activities:
        count = enrollment_count(activity.id)
        status_text, status_class = activity_status(activity)
        # 活动结束后不再显示在主界面；历史记录统一在活动管理/出勤统计中查看。
        if status_text == "活动结束":
            continue
        # 普通用户主界面只显示开放报名、锁定和进行中的活动，并按活动可见范围过滤；管理员可查看全部活动用于管理。
        if not current_user.is_admin:
            if status_text not in {"报名中", "已锁定", "活动进行中"}:
                continue
            if not can_view_activity(activity):
                continue
        activity_cards.append({"activity": activity, "count": count, "status_text": status_text, "status_class": status_class})

    plan_cards = []
    plans_query = ActivityPlan.query.order_by(ActivityPlan.created_at.desc()).all()
    for plan in plans_query:
        status_text, status_class = plan_status(plan)
        if plan.hidden and not current_user.is_admin:
            continue
        if plan.hidden and current_user.is_admin:
            continue  # 隐藏的策划放到活动管理页面恢复，主界面不显示。
        if not current_user.is_admin and not can_view_plan(plan):
            continue
        plan_cards.append({"plan": plan, "status_text": status_text, "status_class": status_class})

    venues = Venue.query.order_by(Venue.name.asc()).all()
    game_modes = GameMode.query.order_by(GameMode.name.asc()).all()
    extraction_setting = get_extraction_ui_setting() if current_user.can_access_extraction else None
    return render_template(
        "dashboard.html",
        users_count=users_count,
        online_count=online_count,
        activity_cards=activity_cards,
        plan_cards=plan_cards,
        extraction_setting=extraction_setting,
        venues=venues,
        game_modes=game_modes,
        users=User.query.filter_by(disabled=False).order_by(User.callsign.asc()).all(),
    )


@app.route("/activities/new")
@login_required
@admin_required
def new_activity():
    venues = Venue.query.order_by(Venue.name.asc()).all()
    game_modes = GameMode.query.order_by(GameMode.name.asc()).all()
    launchers = LauncherRentalItem.query.filter_by(active=True).order_by(LauncherRentalItem.name.asc()).all()
    users = User.query.filter_by(disabled=False).order_by(User.callsign.asc()).all()
    return render_template("activity_form.html", venues=venues, game_modes=game_modes, launchers=launchers, users=users)


@app.route("/plans/new")
@login_required
@admin_required
def new_plan():
    venues = Venue.query.order_by(Venue.name.asc()).all()
    game_modes = GameMode.query.order_by(GameMode.name.asc()).all()
    return render_template("plan_form.html", venues=venues, game_modes=game_modes, users=User.query.filter_by(disabled=False).order_by(User.callsign.asc()).all())


@app.route("/activities/create", methods=["POST"])
@login_required
@admin_required
def create_activity():
    try:
        name = request.form.get("name", "").strip()
        start_at = parse_datetime_local(request.form.get("start_at", ""))
        end_at = parse_datetime_local(request.form.get("end_at", ""))
        venue_id = request.form.get("venue_id", "").strip()
        if venue_id:
            venue = db.session.get(Venue, int(venue_id))
            location = venue.name if venue else request.form.get("location", "").strip()
        else:
            location = request.form.get("location", "").strip()
        open_min = int(request.form.get("open_min", "0"))
        camp_count = int(request.form.get("camp_count", "0"))
        camp_limit = int(request.form.get("camp_limit", "0"))
        squad_count = int(request.form.get("squad_count", "0"))
        squad_limit = int(request.form.get("squad_limit", "0"))
        allowed_jobs = request.form.getlist("allowed_jobs")
        game_mode_names = []
        for mode_id in request.form.getlist("game_mode_ids"):
            mode = db.session.get(GameMode, int(mode_id)) if mode_id else None
            if mode:
                game_mode_names.append(mode.name)
        launcher_ids = request.form.getlist("launcher_ids")
        activity_region = request.form.get("activity_region", "宁波").strip() or "宁波"
        if activity_region not in ACTIVITY_REGION_OPTIONS:
            raise ValueError("请选择有效的活动地区。")
        visibility_type, invitee_ids = parse_activity_visibility_from_form()

        if not name or not location:
            raise ValueError("活动名称和地点不能为空。")
        if end_at <= start_at:
            raise ValueError("活动结束时间必须晚于开始时间。")
        if min(open_min, camp_count, camp_limit, squad_count, squad_limit) <= 0:
            raise ValueError("人数和数量必须大于 0。")
        if not allowed_jobs:
            raise ValueError("至少选择一个开放职业。")
        for job in allowed_jobs:
            if job not in JOB_OPTIONS:
                raise ValueError("包含无效职业。")

        activity = Activity(
            name=name,
            start_at=start_at,
            end_at=end_at,
            location=location,
            open_min=open_min,
            camp_count=camp_count,
            camp_limit=camp_limit,
            squad_count=squad_count,
            squad_limit=squad_limit,
            allowed_jobs=",".join(allowed_jobs),
            game_modes=",".join(game_mode_names),
            attendance_enabled=True,
            activity_region=activity_region,
            visibility_type=visibility_type,
            invitee_ids=invitee_ids,
            created_by_id=current_user.id,
        )
        db.session.add(activity)
        db.session.flush()
        ensure_activity_settings(activity)
        replace_activity_launcher_options(activity.id, launcher_ids)
        db.session.commit()
        flash("活动创建成功。", "success")
    except Exception as exc:
        db.session.rollback()
        flash(str(exc), "error")
    return redirect(url_for("dashboard"))


@app.route("/activities/<int:activity_id>")
@login_required
def activity_detail(activity_id):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        flash("活动不存在。", "error")
        return redirect(url_for("dashboard"))
    if not can_view_activity(activity):
        flash("你没有查看该活动的权限。", "error")
        return redirect(url_for("dashboard"))
    ensure_activity_settings(activity)
    db.session.commit()
    status_text, status_class = activity_status(activity)
    my_enrollment = Enrollment.query.filter_by(activity_id=activity.id, user_id=current_user.id).first()
    launchers = activity_available_launchers(activity.id)
    all_launchers = LauncherRentalItem.query.filter_by(active=True).order_by(LauncherRentalItem.name.asc()).all()
    allowed_launcher_ids = activity_allowed_launcher_ids(activity.id)
    unavailable_ids = unavailable_launcher_ids()
    rental_setting = get_launcher_rental_setting()
    launcher_rentals = activity_launcher_rental_rows(activity.id)
    my_launcher_rental = ActivityLauncherRental.query.filter_by(activity_id=activity.id, user_id=current_user.id, cancelled_at=None).first()
    db.session.commit()
    return render_template(
        "activity.html",
        activity=activity,
        status_text=status_text,
        status_class=status_class,
        my_enrollment=my_enrollment,
        signup_count=enrollment_count(activity.id),
        can_join=can_user_join(activity),
        can_cancel=can_user_cancel(activity),
        can_self_adjust=can_self_adjust(activity),
        launchers=launchers,
        all_launchers=all_launchers,
        allowed_launcher_ids=allowed_launcher_ids,
        unavailable_launcher_ids=unavailable_ids,
        rental_setting=rental_setting,
        launcher_rentals=launcher_rentals,
        my_launcher_rental=my_launcher_rental,
        visibility_label=activity_visibility_label(activity),
        invited_users=get_activity_invited_users(activity),
        can_view_visibility_info=can_view_activity_visibility_info(activity),
        users=User.query.filter_by(disabled=False).order_by(User.callsign.asc()).all(),
        game_modes=GameMode.query.order_by(GameMode.name.asc()).all(),
        venues=Venue.query.order_by(Venue.name.asc()).all(),
    )


@app.route("/activities/<int:activity_id>/basic-info", methods=["POST"])
@login_required
@admin_required
def update_activity_basic_info(activity_id):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        flash("活动不存在。", "error")
        return redirect(url_for("dashboard"))
    try:
        name = request.form.get("name", "").strip()
        start_at = parse_datetime_local(request.form.get("start_at", ""))
        end_at = parse_datetime_local(request.form.get("end_at", ""))
        venue_id = request.form.get("venue_id", "").strip()
        if venue_id:
            venue = db.session.get(Venue, int(venue_id))
            location = venue.name if venue else request.form.get("location", "").strip()
        else:
            location = request.form.get("location", "").strip()
        open_min = int(request.form.get("open_min", "0"))
        allowed_jobs = request.form.getlist("allowed_jobs")
        game_mode_names = []
        for mode_id in request.form.getlist("game_mode_ids"):
            mode = db.session.get(GameMode, int(mode_id)) if mode_id else None
            if mode:
                game_mode_names.append(mode.name)
        activity_region = request.form.get("activity_region", "宁波").strip() or "宁波"
        if activity_region not in ACTIVITY_REGION_OPTIONS:
            raise ValueError("请选择有效的活动地区。")
        if not name or not location:
            raise ValueError("活动名称和地点不能为空。")
        if end_at <= start_at:
            raise ValueError("活动结束时间必须晚于开始时间。")
        if open_min <= 0:
            raise ValueError("活动开启人数必须大于 0。")
        if not allowed_jobs:
            raise ValueError("至少选择一个开放职业。")
        for job in allowed_jobs:
            if job not in JOB_OPTIONS:
                raise ValueError("包含无效职业。")

        activity.name = name
        activity.start_at = start_at
        activity.end_at = end_at
        activity.location = location
        activity.open_min = open_min
        activity.allowed_jobs = ",".join(allowed_jobs)
        activity.game_modes = ",".join(game_mode_names)
        activity.activity_region = activity_region

        attendance_event = AttendanceEvent.query.filter_by(source_activity_id=activity.id).first()
        if attendance_event:
            attendance_event.name = activity.name
            attendance_event.event_date = activity.start_at.date()
            attendance_event.location = activity.location
            attendance_event.activity_region = activity.activity_region or "宁波"
            attendance_event.organizer = activity.created_by.callsign if activity.created_by else attendance_event.organizer
        db.session.commit()
        flash("活动基本信息已保存。", "success")
    except Exception as exc:
        db.session.rollback()
        flash(str(exc), "error")
    return redirect(url_for("activity_detail", activity_id=activity.id))


@app.route("/activities/<int:activity_id>/attendance-enabled", methods=["POST"])
@login_required
@admin_required
def update_activity_attendance_enabled(activity_id):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        flash("活动不存在。", "error")
        return redirect(url_for("dashboard"))
    try:
        activity_region = request.form.get("activity_region", "宁波").strip() or "宁波"
        if activity_region not in ACTIVITY_REGION_OPTIONS:
            raise ValueError("请选择有效的活动地区。")
        activity.activity_region = activity_region
        # 兼容旧字段：新版以活动地区作为出勤统计字段，正式成功结束活动默认计入统计。
        activity.attendance_enabled = True
        attendance_event = AttendanceEvent.query.filter_by(source_activity_id=activity.id).first()
        if attendance_event:
            attendance_event.activity_region = activity_region
        db.session.commit()
        try:
            sync_attendance_events()
        except Exception:
            db.session.rollback()
        flash("活动地区已更新。", "success")
    except Exception as exc:
        db.session.rollback()
        flash(f"更新活动地区失败：{exc}", "error")
    return redirect(url_for("activity_detail", activity_id=activity.id))


@app.route("/activities/<int:activity_id>/visibility", methods=["POST"])
@login_required
@admin_required
def update_activity_visibility(activity_id):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        flash("活动不存在。", "error")
        return redirect(url_for("dashboard"))
    try:
        visibility_type, invitee_ids = parse_activity_visibility_from_form()
        activity.visibility_type = visibility_type
        activity.invitee_ids = invitee_ids
        db.session.commit()
        flash("活动可见范围已保存。", "success")
    except Exception as exc:
        db.session.rollback()
        flash(str(exc), "error")
    return redirect(url_for("activity_detail", activity_id=activity.id))


def cancel_signup_for_current_user(activity):
    enrollment = Enrollment.query.filter_by(activity_id=activity.id, user_id=current_user.id).first()
    if not enrollment:
        return False, "你还没有报名该活动。"

    if not current_user.is_admin and not can_user_cancel(activity):
        return False, "当前活动状态不允许取消报名。"

    old_camp = enrollment.camp_no
    old_squad = enrollment.squad_no
    ActivityLauncherRental.query.filter_by(activity_id=activity.id, user_id=current_user.id, cancelled_at=None).update(
        {"cancelled_at": datetime.now()}, synchronize_session=False
    )
    db.session.delete(enrollment)
    db.session.flush()
    reassign_leader_if_needed(activity, old_camp, old_squad)
    db.session.commit()
    return True, "已取消报名。"


@app.route("/activities/<int:activity_id>/cancel", methods=["POST"])
@login_required
def cancel_activity_signup(activity_id):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        flash("活动不存在。", "error")
        return redirect(url_for("dashboard"))
    try:
        ok, message = cancel_signup_for_current_user(activity)
        flash(message, "success" if ok else "error")
    except Exception as exc:
        db.session.rollback()
        flash(f"取消报名失败：{exc}", "error")
    return redirect(url_for("activity_detail", activity_id=activity_id))




@app.route("/activities/<int:activity_id>/rent-launcher", methods=["POST"])
@login_required
def rent_launcher_for_activity(activity_id):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        flash("活动不存在。", "error")
        return redirect(url_for("dashboard"))
    if datetime.now() > activity.end_at:
        flash("活动已结束，不能租赁发射器。", "error")
        return redirect(url_for("activity_detail", activity_id=activity.id))
    if not Enrollment.query.filter_by(activity_id=activity.id, user_id=current_user.id).first():
        flash("请先报名活动，再租赁发射器。", "error")
        return redirect(url_for("activity_detail", activity_id=activity.id))
    launcher_id = int(request.form.get("launcher_id", "0") or 0)
    launcher = db.session.get(LauncherRentalItem, launcher_id)
    if not launcher or not launcher.active:
        flash("发射器不存在或已停用。", "error")
        return redirect(url_for("activity_detail", activity_id=activity.id))
    if launcher.id not in activity_allowed_launcher_ids(activity.id):
        flash("该发射器未对本活动开放租赁。", "error")
        return redirect(url_for("activity_detail", activity_id=activity.id))
    if ActivityLauncherRental.query.filter_by(activity_id=activity.id, user_id=current_user.id, cancelled_at=None).first():
        flash("你已经租赁了发射器，如需更换请先取消当前租赁。", "error")
        return redirect(url_for("activity_detail", activity_id=activity.id))
    if not is_launcher_available(launcher.id):
        flash("该发射器已被其他活动用户租赁，活动结束前不能再次选择。", "error")
        return redirect(url_for("activity_detail", activity_id=activity.id))
    rental = ActivityLauncherRental(activity_id=activity.id, launcher_id=launcher.id, user_id=current_user.id)
    db.session.add(rental)
    db.session.commit()
    flash("发射器租赁成功。", "success")
    return redirect(url_for("activity_detail", activity_id=activity.id))


@app.route("/activities/<int:activity_id>/launcher-rentals/<int:rental_id>/cancel", methods=["POST"])
@login_required
def cancel_launcher_rental(activity_id, rental_id):
    activity = db.session.get(Activity, activity_id)
    rental = db.session.get(ActivityLauncherRental, rental_id)
    if not activity or not rental or rental.activity_id != activity.id or rental.cancelled_at is not None:
        flash("租赁记录不存在。", "error")
        return redirect(url_for("dashboard"))
    if not (current_user.is_admin or rental.user_id == current_user.id):
        flash("无权取消该租赁。", "error")
        return redirect(url_for("activity_detail", activity_id=activity.id))
    if datetime.now() > activity.end_at and not current_user.is_admin:
        flash("活动已结束，租赁已自动释放。", "error")
        return redirect(url_for("activity_detail", activity_id=activity.id))
    rental.cancelled_at = datetime.now()
    db.session.commit()
    flash("发射器租赁已取消。", "success")
    return redirect(url_for("activity_detail", activity_id=activity.id))


@app.route("/api/activities/<int:activity_id>/roster")
@login_required
def api_roster(activity_id):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        return jsonify({"ok": False, "message": "活动不存在。"}), 404
    if not can_view_activity(activity):
        return jsonify({"ok": False, "message": "你没有查看该活动的权限。"}), 403

    ensure_activity_settings(activity)
    db.session.commit()

    squad_settings = SquadSetting.query.filter_by(activity_id=activity.id).all()
    leader_rank = {
        (s.camp_no, s.squad_no, s.leader_user_id): 0
        for s in squad_settings
        if s.leader_user_id
    }

    enrollments = (
        Enrollment.query.filter_by(activity_id=activity.id)
        .join(User)
        .order_by(Enrollment.camp_no.is_(None), Enrollment.camp_no, Enrollment.squad_no.is_(None), Enrollment.squad_no, Enrollment.created_at.asc())
        .all()
    )
    rows = []
    for e in enrollments:
        is_leader = leader_rank.get((e.camp_no, e.squad_no, e.user_id), 1) == 0
        rows.append(
            {
                "id": e.id,
                "user_id": e.user_id,
                "callsign": e.user.callsign,
                "username": e.user.username,
                "camp_no": e.camp_no,
                "squad_no": e.squad_no,
                "job": e.job,
                "is_squad_leader": is_leader,
                "created_at": e.created_at.strftime("%Y-%m-%d %H:%M"),
            }
        )

    # 队长排第一，其余按报名时间。
    rows.sort(key=lambda r: (r["camp_no"] is None, r["camp_no"] or 999, r["squad_no"] is None, r["squad_no"] or 999, 0 if r["is_squad_leader"] else 1, r["created_at"], r["callsign"]))

    camp_counts = {str(i): camp_member_count(activity.id, i) for i in range(1, activity.camp_count + 1)}
    squad_counts = {
        f"{camp}-{squad}": squad_member_count(activity.id, camp, squad)
        for camp in range(1, activity.camp_count + 1)
        for squad in range(1, activity.squad_count + 1)
    }

    camp_settings = {}
    for camp in range(1, activity.camp_count + 1):
        setting = get_or_create_camp_setting(activity.id, camp)
        commander = db.session.get(User, setting.commander_user_id) if setting.commander_user_id else None
        camp_settings[str(camp)] = {
            "commander_user_id": setting.commander_user_id,
            "commander_callsign": commander.callsign if commander else "",
        }

    squad_settings_map = {}
    for camp in range(1, activity.camp_count + 1):
        for squad in range(1, activity.squad_count + 1):
            setting = get_or_create_squad_setting(activity.id, camp, squad)
            leader = db.session.get(User, setting.leader_user_id) if setting.leader_user_id else None
            squad_settings_map[f"{camp}-{squad}"] = {
                "name": setting.name,
                "radio_channel": setting.radio_channel,
                "leader_user_id": setting.leader_user_id,
                "leader_callsign": leader.callsign if leader else "",
                "locked": bool(setting.locked),
                "can_edit_settings": current_user.is_admin or (setting.leader_user_id == current_user.id),
            }
    db.session.commit()

    status_text, status_class = activity_status(activity)

    return jsonify(
        {
            "ok": True,
            "activity": {
                "id": activity.id,
                "status_text": status_text,
                "status_class": status_class,
                "signup_count": len(rows),
                "open_min": activity.open_min,
                "camp_count": activity.camp_count,
                "camp_limit": activity.camp_limit,
                "squad_count": activity.squad_count,
                "squad_limit": activity.squad_limit,
                "allowed_jobs": activity.allowed_job_list,
                "list_locked": activity.list_locked,
            },
            "current_user_id": current_user.id,
            "is_admin": current_user.is_admin,
            "can_join": can_user_join(activity),
            "can_cancel": can_user_cancel(activity),
            "can_self_adjust": can_self_adjust(activity),
            "camp_counts": camp_counts,
            "squad_counts": squad_counts,
            "camp_settings": camp_settings,
            "squad_settings": squad_settings_map,
            "rows": rows,
        }
    )


@app.route("/api/activities/<int:activity_id>/join", methods=["POST"])
@login_required
def api_join(activity_id):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        return jsonify({"ok": False, "message": "活动不存在。"}), 404
    if not can_user_join(activity):
        return jsonify({"ok": False, "message": "当前活动不可报名。"}), 400
    if Enrollment.query.filter_by(activity_id=activity.id, user_id=current_user.id).first():
        return jsonify({"ok": False, "message": "你已经报名该活动。"}), 400

    enrollment = Enrollment(activity_id=activity.id, user_id=current_user.id, rent_launcher=False)
    db.session.add(enrollment)
    db.session.commit()
    return jsonify({"ok": True, "message": "报名成功。"})


@app.route("/api/activities/<int:activity_id>/cancel", methods=["POST"])
@login_required
def api_cancel_join(activity_id):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        return jsonify({"ok": False, "message": "活动不存在。"}), 404
    try:
        ok, message = cancel_signup_for_current_user(activity)
        if not ok:
            return jsonify({"ok": False, "message": message}), 400
        return jsonify({"ok": True, "message": message})
    except Exception as exc:
        db.session.rollback()
        return jsonify({"ok": False, "message": f"取消报名失败：{exc}"}), 500




@app.route("/api/activities/<int:activity_id>/organization", methods=["POST"])
@login_required
@admin_required
def api_update_activity_organization(activity_id):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        return jsonify({"ok": False, "message": "活动不存在。"}), 404
    data = request.get_json(silent=True) or {}
    try:
        camp_count = int(data.get("camp_count", activity.camp_count))
        squad_count = int(data.get("squad_count", activity.squad_count))
        camp_limit = int(data.get("camp_limit", activity.camp_limit))
        squad_limit = int(data.get("squad_limit", activity.squad_limit))
        if min(camp_count, squad_count, camp_limit, squad_limit) <= 0:
            return jsonify({"ok": False, "message": "阵营数、小队数和人数上限必须大于 0。"}), 400
        if camp_count > 20 or squad_count > 30:
            return jsonify({"ok": False, "message": "阵营数或小队数过大，请控制在合理范围内。"}), 400

        changed = (
            activity.camp_count != camp_count or
            activity.squad_count != squad_count or
            activity.camp_limit != camp_limit or
            activity.squad_limit != squad_limit
        )
        activity.camp_count = camp_count
        activity.squad_count = squad_count
        activity.camp_limit = camp_limit
        activity.squad_limit = squad_limit

        # 只要管理员提交了组织结构修改，就按需求把人员重置为未分配，方便重新分配。
        Enrollment.query.filter_by(activity_id=activity.id).update(
            {"camp_no": None, "squad_no": None, "updated_at": datetime.now()},
            synchronize_session=False,
        )
        CampSetting.query.filter_by(activity_id=activity.id).delete(synchronize_session=False)
        SquadSetting.query.filter_by(activity_id=activity.id).delete(synchronize_session=False)
        db.session.flush()
        ensure_activity_settings(activity)
        db.session.commit()
        msg = "活动组织结构已更新，所有人员已重置为未分配。" if changed else "已重新保存，所有人员已重置为未分配。"
        return jsonify({"ok": True, "message": msg})
    except ValueError:
        db.session.rollback()
        return jsonify({"ok": False, "message": "请输入有效的数字。"}), 400
    except Exception as exc:
        db.session.rollback()
        return jsonify({"ok": False, "message": f"修改失败：{exc}"}), 500


@app.route("/api/enrollments/<int:enrollment_id>/update", methods=["POST"])
@login_required
def api_update_enrollment(enrollment_id):
    enrollment = db.session.get(Enrollment, enrollment_id)
    if not enrollment:
        return jsonify({"ok": False, "message": "报名记录不存在。"}), 404
    activity = enrollment.activity

    is_owner = enrollment.user_id == current_user.id
    if not current_user.is_admin:
        if not is_owner:
            return jsonify({"ok": False, "message": "无权修改其他人的报名信息。"}), 403
        if not can_self_adjust(activity):
            return jsonify({"ok": False, "message": "人员列表已锁定，已报名人员不能自行调整。"}), 400

    data = request.get_json(silent=True) or {}
    try:
        camp_no = data.get("camp_no", None)
        squad_no = data.get("squad_no", None)
        job = data.get("job", None)
        old_camp = enrollment.camp_no
        old_squad = enrollment.squad_no
        new_camp, new_squad, new_job = validate_assignment(activity, enrollment, camp_no, squad_no, job)
        if camp_no is not None:
            enrollment.camp_no = new_camp
        if squad_no is not None:
            enrollment.squad_no = new_squad
        if job is not None:
            enrollment.job = new_job
        enrollment.updated_at = datetime.now()
        db.session.flush()
        if (camp_no is not None or squad_no is not None):
            assign_default_leader_if_needed(activity, enrollment.camp_no, enrollment.squad_no, enrollment.user_id)
            if old_camp != enrollment.camp_no or old_squad != enrollment.squad_no:
                reassign_leader_if_needed(activity, old_camp, old_squad)
        db.session.commit()
        return jsonify({"ok": True, "message": "已保存。"})
    except Exception as exc:
        db.session.rollback()
        return jsonify({"ok": False, "message": str(exc)}), 400


@app.route("/api/activities/<int:activity_id>/camp/<int:camp_no>/commander", methods=["POST"])
@login_required
@admin_required
def api_update_camp_commander(activity_id, camp_no):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        return jsonify({"ok": False, "message": "活动不存在。"}), 404
    if camp_no < 1 or camp_no > activity.camp_count:
        return jsonify({"ok": False, "message": "阵营不存在。"}), 400
    data = request.get_json(silent=True) or {}
    commander_user_id = data.get("commander_user_id")
    setting = get_or_create_camp_setting(activity.id, camp_no)
    if commander_user_id in (None, "", 0, "0"):
        setting.commander_user_id = None
    else:
        commander_user_id = int(commander_user_id)
        enrollment = Enrollment.query.filter_by(activity_id=activity.id, camp_no=camp_no, user_id=commander_user_id).first()
        if not enrollment:
            return jsonify({"ok": False, "message": "阵营指挥必须是该阵营内已报名人员。"}), 400
        setting.commander_user_id = commander_user_id
    setting.updated_at = datetime.now()
    db.session.commit()
    return jsonify({"ok": True, "message": "阵营指挥已更新。"})


@app.route("/api/activities/<int:activity_id>/squad/<int:camp_no>/<int:squad_no>/settings", methods=["POST"])
@login_required
def api_update_squad_settings(activity_id, camp_no, squad_no):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        return jsonify({"ok": False, "message": "活动不存在。"}), 404
    if camp_no < 1 or camp_no > activity.camp_count or squad_no < 1 or squad_no > activity.squad_count:
        return jsonify({"ok": False, "message": "小队不存在。"}), 400
    setting = get_or_create_squad_setting(activity.id, camp_no, squad_no)
    if not (current_user.is_admin or setting.leader_user_id == current_user.id):
        return jsonify({"ok": False, "message": "只有管理员或该小队队长可以修改小队名称和无线电频道。"}), 403
    data = request.get_json(silent=True) or {}
    name = str(data.get("name", setting.name)).strip()
    radio_channel = str(data.get("radio_channel", setting.radio_channel)).strip()
    if not name:
        return jsonify({"ok": False, "message": "小队名称不能为空。"}), 400
    if not radio_channel:
        return jsonify({"ok": False, "message": "无线电频道不能为空。"}), 400
    setting.name = name[:80]
    setting.radio_channel = radio_channel[:30]
    setting.updated_at = datetime.now()
    db.session.commit()
    return jsonify({"ok": True, "message": "小队信息已更新。"})


@app.route("/api/activities/<int:activity_id>/squad/<int:camp_no>/<int:squad_no>/leader", methods=["POST"])
@login_required
@admin_required
def api_update_squad_leader(activity_id, camp_no, squad_no):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        return jsonify({"ok": False, "message": "活动不存在。"}), 404
    if camp_no < 1 or camp_no > activity.camp_count or squad_no < 1 or squad_no > activity.squad_count:
        return jsonify({"ok": False, "message": "小队不存在。"}), 400
    data = request.get_json(silent=True) or {}
    leader_user_id = data.get("leader_user_id")
    setting = get_or_create_squad_setting(activity.id, camp_no, squad_no)
    if leader_user_id in (None, "", 0, "0"):
        setting.leader_user_id = None
    else:
        leader_user_id = int(leader_user_id)
        enrollment = Enrollment.query.filter_by(activity_id=activity.id, camp_no=camp_no, squad_no=squad_no, user_id=leader_user_id).first()
        if not enrollment:
            return jsonify({"ok": False, "message": "小队队长必须是该小队内已报名人员。"}), 400
        setting.leader_user_id = leader_user_id
    setting.updated_at = datetime.now()
    db.session.commit()
    return jsonify({"ok": True, "message": "小队队长已更新。"})


@app.route("/api/activities/<int:activity_id>/squad/<int:camp_no>/<int:squad_no>/toggle-lock", methods=["POST"])
@login_required
@admin_required
def api_toggle_squad_lock(activity_id, camp_no, squad_no):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        return jsonify({"ok": False, "message": "活动不存在。"}), 404
    if camp_no < 1 or camp_no > activity.camp_count or squad_no < 1 or squad_no > activity.squad_count:
        return jsonify({"ok": False, "message": "小队不存在。"}), 400
    setting = get_or_create_squad_setting(activity.id, camp_no, squad_no)
    setting.locked = not setting.locked
    setting.updated_at = datetime.now()
    db.session.commit()
    return jsonify({
        "ok": True,
        "locked": bool(setting.locked),
        "message": "小队已锁定。" if setting.locked else "小队已解除锁定。",
    })


@app.route("/api/activities/<int:activity_id>/toggle-lock", methods=["POST"])
@login_required
@admin_required
def api_toggle_lock(activity_id):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        return jsonify({"ok": False, "message": "活动不存在。"}), 404
    activity.list_locked = not activity.list_locked
    db.session.commit()
    return jsonify({"ok": True, "list_locked": activity.list_locked})


@app.route("/users")
@login_required
@admin_required
def users():
    all_users = User.query.order_by(User.created_at.desc()).all()
    invites = InviteCode.query.order_by(InviteCode.created_at.desc()).limit(50).all()
    return render_template("users.html", users=all_users, invites=invites)


@app.route("/invites/create", methods=["POST"])
@login_required
@admin_required
def create_invite():
    role = request.form.get("role", "user")
    if role not in {"user", "admin"}:
        role = "user"
    if role == "admin" and not current_user.is_superadmin:
        role = "user"
    try:
        max_uses = int(request.form.get("max_uses", "1"))
    except ValueError:
        max_uses = 1
    max_uses = max(1, min(max_uses, 999))
    invite = InviteCode(code=generate_invite_code(), role=role, max_uses=max_uses, created_by_id=current_user.id)
    db.session.add(invite)
    db.session.commit()
    flash(f"已生成邀请码：{invite.code}，可使用 {invite.max_uses} 次。", "success")
    return redirect(url_for("users"))




@app.route("/invites/<int:invite_id>/delete", methods=["POST"])
@login_required
@superadmin_required
def delete_invite(invite_id):
    invite = db.session.get(InviteCode, invite_id)
    if not invite:
        flash("邀请码不存在或已被删除。", "error")
        return redirect(url_for("users"))
    code = invite.code
    db.session.delete(invite)
    db.session.commit()
    flash(f"邀请码 {code} 已删除并失效，已注册账号不受影响。", "success")
    return redirect(url_for("users"))


@app.route("/users/<int:user_id>/callsign", methods=["POST"])
@login_required
@superadmin_required
def update_user_callsign(user_id):
    user = db.session.get(User, user_id)
    if not user:
        flash("用户不存在。", "error")
        return redirect(url_for("users"))
    callsign = (request.form.get("callsign") or "").strip()
    if not callsign:
        flash("呼号不能为空。", "error")
        return redirect(url_for("users"))
    if len(callsign) > 80:
        flash("呼号不能超过 80 个字符。", "error")
        return redirect(url_for("users"))
    old_callsign = user.callsign
    user.callsign = callsign
    db.session.commit()
    flash(f"已将 {old_callsign} 的呼号修改为 {callsign}。", "success")
    return redirect(url_for("users"))


@app.route("/users/<int:user_id>/reset-password", methods=["POST"])
@login_required
@superadmin_required
def reset_user_password(user_id):
    user = db.session.get(User, user_id)
    if not user:
        flash("用户不存在。", "error")
        return redirect(url_for("users"))
    if user.id == current_user.id:
        flash("不能在用户管理中重置当前登录账号的密码，请到账号设置中修改。", "error")
        return redirect(url_for("users"))
    user.set_password("nb123456")
    db.session.commit()
    flash(f"已将 {user.callsign} 的密码重置为 nb123456。", "success")
    return redirect(url_for("users"))


@app.route("/users/<int:user_id>/role", methods=["POST"])
@login_required
@superadmin_required
def update_user_role(user_id):
    user = db.session.get(User, user_id)
    if not user:
        flash("用户不存在。", "error")
    elif user.id == current_user.id:
        flash("不能修改自己的角色。", "error")
    else:
        role = request.form.get("role", "user")
        if role in {"user", "admin", "superadmin"}:
            user.role = role
            db.session.commit()
            flash("用户角色已更新。", "success")
        else:
            flash("无效角色。", "error")
    return redirect(url_for("users"))




@app.route("/users/<int:user_id>/admin-kind", methods=["POST"])
@login_required
@superadmin_required
def update_user_admin_kind(user_id):
    user = db.session.get(User, user_id)
    if not user:
        flash("用户不存在。", "error")
        return redirect(url_for("users"))
    if user.id == current_user.id:
        flash("不能修改自己的管理员权限。", "error")
        return redirect(url_for("users"))

    kind = request.form.get("admin_kind", "user")
    valid = {"user", "admin", "superadmin", "attendance_manager", "extraction_manager"}
    if kind not in valid:
        flash("无效管理员权限。", "error")
        return redirect(url_for("users"))

    # 单一下拉框控制用户权限类型；出勤管理员和逃离西撇镇管理员默认具备普通管理员权限。
    user.attendance_manager = False
    user.extraction_manager = False

    if kind == "user":
        user.role = "user"
    elif kind == "admin":
        user.role = "admin"
    elif kind == "superadmin":
        user.role = "superadmin"
    elif kind == "attendance_manager":
        user.role = "admin"
        user.attendance_manager = True
    elif kind == "extraction_manager":
        user.role = "admin"
        user.extraction_manager = True
        user.extraction_authorized = True

    db.session.commit()
    flash("管理员权限已更新。", "success")
    return redirect(url_for("users"))


@app.route("/users/<int:user_id>/toggle-disabled", methods=["POST"])
@login_required
@admin_required
def toggle_user_disabled(user_id):
    user = db.session.get(User, user_id)
    if not user:
        flash("用户不存在。", "error")
    elif user.id == current_user.id:
        flash("不能禁用自己。", "error")
    elif user.is_superadmin and not current_user.is_superadmin:
        flash("普通管理员不能禁用超级管理员。", "error")
    elif user.is_admin and not current_user.is_superadmin:
        flash("普通管理员只能禁用普通用户。", "error")
    else:
        user.disabled = not user.disabled
        db.session.commit()
        flash("用户状态已更新。", "success")
    return redirect(url_for("users"))


@app.route("/users/<int:user_id>/delete", methods=["POST"])
@login_required
@admin_required
def delete_user(user_id):
    user = db.session.get(User, user_id)
    if not user:
        flash("用户不存在。", "error")
    elif user.id == current_user.id:
        flash("不能删除自己。", "error")
    elif user.is_superadmin and not current_user.is_superadmin:
        flash("普通管理员不能删除超级管理员。", "error")
    elif user.is_admin and not current_user.is_superadmin:
        flash("普通管理员只能删除普通用户。", "error")
    else:
        Enrollment.query.filter_by(user_id=user.id).delete()
        InviteCode.query.filter_by(used_by_id=user.id).update({"used_by_id": None})
        db.session.delete(user)
        db.session.commit()
        flash("用户已删除。", "success")
    return redirect(url_for("users"))




@app.route("/users/<int:user_id>/toggle-regular", methods=["POST"])
@login_required
@admin_required
def toggle_regular_member(user_id):
    user = db.session.get(User, user_id)
    if not user:
        flash("用户不存在。", "error")
    elif user.is_superadmin and not current_user.is_superadmin:
        flash("普通管理员不能修改超级管理员。", "error")
    else:
        user.is_regular_member = not bool(user.is_regular_member)
        db.session.commit()
        flash("正式队员状态已更新。", "success")
    return redirect(url_for("users"))


@app.route("/users/<int:user_id>/toggle-attendance-manager", methods=["POST"])
@login_required
@superadmin_required
def toggle_attendance_manager(user_id):
    user = db.session.get(User, user_id)
    if not user:
        flash("用户不存在。", "error")
    elif user.id == current_user.id:
        flash("不能修改自己的出勤率管理权限。", "error")
    else:
        user.attendance_manager = not bool(user.attendance_manager)
        db.session.commit()
        flash("出勤率管理权限已更新。", "success")
    return redirect(url_for("users"))


@app.route("/attendance")
@login_required
def attendance():
    if not can_view_attendance_page():
        flash("请先登录后查看出勤记录。", "error")
        return redirect(url_for("dashboard"))
    try:
        sync_attendance_events()
    except Exception:
        db.session.rollback()

    # v45: 正式队员保持原来的完整出勤统计视图；
    # 只有非正式普通用户使用“只看自己参加活动”的个人视图。
    can_manage_attendance = current_user.can_manage_attendance
    is_personal_attendance = not (can_manage_attendance or bool(getattr(current_user, "is_regular_member", False)))
    regular_only = request.args.get("regular") == "1"
    selected_year_raw = request.args.get("year", "").strip()
    selected_year = int(selected_year_raw) if selected_year_raw.isdigit() else None
    selected_region = request.args.get("region", "").strip()
    if selected_region not in ACTIVITY_REGION_OPTIONS:
        selected_region = ""

    base_events_query = AttendanceEvent.query

    # 非正式普通用户只能看到自己已经参加过的活动记录，避免看到未对其开放的活动。
    # 正式队员保持完整统计视图。
    if is_personal_attendance:
        own_present_records = AttendanceRecord.query.filter_by(user_id=current_user.id, present=True).all()
        own_event_ids = [r.event_id for r in own_present_records]
        if own_event_ids:
            base_events_query = base_events_query.filter(AttendanceEvent.id.in_(own_event_ids))
        else:
            base_events_query = base_events_query.filter(False)

    all_events_for_years = base_events_query.order_by(AttendanceEvent.event_date.desc(), AttendanceEvent.id.desc()).all()
    available_years = sorted({e.event_date.year for e in all_events_for_years}, reverse=True)

    events_query = AttendanceEvent.query
    if is_personal_attendance:
        visible_event_ids = [e.id for e in all_events_for_years]
        if visible_event_ids:
            events_query = events_query.filter(AttendanceEvent.id.in_(visible_event_ids))
        else:
            events_query = events_query.filter(False)

    if selected_year:
        year_start = datetime(selected_year, 1, 1).date()
        year_end = datetime(selected_year + 1, 1, 1).date()
        events_query = events_query.filter(AttendanceEvent.event_date >= year_start, AttendanceEvent.event_date < year_end)
    if selected_region:
        events_query = events_query.filter(AttendanceEvent.activity_region == selected_region)
    events = events_query.order_by(AttendanceEvent.event_date.asc(), AttendanceEvent.id.asc()).all()
    event_ids = [e.id for e in events]

    if not is_personal_attendance:
        users_query = User.query.filter_by(disabled=False)
        if regular_only:
            users_query = users_query.filter(User.is_regular_member.is_(True))
        users_list = users_query.order_by(User.is_regular_member.desc(), User.callsign.asc()).all()
    else:
        users_list = [current_user]
        regular_only = False

    records_query = AttendanceRecord.query.filter(AttendanceRecord.present.is_(True))
    if event_ids:
        records_query = records_query.filter(AttendanceRecord.event_id.in_(event_ids))
    else:
        records_query = records_query.filter(False)
    if is_personal_attendance:
        records_query = records_query.filter(AttendanceRecord.user_id == current_user.id)
    records = records_query.all()

    present_map = {(r.user_id, r.event_id): True for r in records}
    attendance_counts = {}
    event_total_counts = {}
    event_regular_counts = {}
    regular_members = User.query.filter_by(disabled=False, is_regular_member=True).order_by(User.callsign.asc()).all()
    regular_user_ids = {u.id for u in regular_members}
    regular_callsign_by_id = {u.id: u.callsign for u in regular_members}
    for r in records:
        attendance_counts[r.user_id] = attendance_counts.get(r.user_id, 0) + 1
        event_total_counts[r.event_id] = event_total_counts.get(r.event_id, 0) + 1
        if r.user_id in regular_user_ids:
            event_regular_counts[r.event_id] = event_regular_counts.get(r.event_id, 0) + 1

    if not is_personal_attendance:
        top_regular_attendance = sorted(
            [(regular_callsign_by_id[uid], attendance_counts.get(uid, 0)) for uid in regular_user_ids],
            key=lambda item: (-item[1], item[0])
        )[:3]

        organizer_counts = {}
        location_popularity = {}
        for e in events:
            organizer = (e.organizer or '').strip()
            if organizer:
                organizer_counts[organizer] = organizer_counts.get(organizer, 0) + 1
            location = (e.location or '').strip()
            if location:
                location_popularity[location] = location_popularity.get(location, 0) + 1

        top_organizers = sorted(organizer_counts.items(), key=lambda item: (-item[1], item[0]))[:3]
        top_locations = sorted(location_popularity.items(), key=lambda item: (-item[1], item[0]))[:3]
        attendance_summary = {
            'scope_label': f'{selected_year} 年' if selected_year else '全部年度',
            'total_events': len(events),
            'top_regular_attendance': top_regular_attendance,
            'top_organizers': top_organizers,
            'top_locations': top_locations,
            'personal_only': False,
        }
    else:
        attendance_summary = {
            'scope_label': f'{selected_year} 年' if selected_year else '全部年度',
            'total_events': attendance_counts.get(current_user.id, 0),
            'top_regular_attendance': [],
            'top_organizers': [],
            'top_locations': [],
            'personal_only': True,
        }

    return render_template(
        "attendance.html",
        users=users_list,
        events=events,
        present_map=present_map,
        regular_only=regular_only,
        selected_year=selected_year,
        selected_region=selected_region,
        available_years=available_years,
        attendance_counts=attendance_counts,
        event_total_counts=event_total_counts,
        event_regular_counts=event_regular_counts,
        regular_members=regular_members,
        attendance_summary=attendance_summary,
        is_personal_attendance=is_personal_attendance,
    )


@app.route("/attendance/manual", methods=["POST"])
@login_required
@attendance_manage_required
def create_manual_attendance_event():
    try:
        name = request.form.get("name", "").strip()
        event_date = datetime.strptime(request.form.get("event_date", ""), "%Y-%m-%d").date()
        location = request.form.get("location", "").strip()
        organizer = request.form.get("organizer", "").strip()
        activity_region = request.form.get("activity_region", "宁波").strip() or "宁波"
        if activity_region not in ACTIVITY_REGION_OPTIONS:
            raise ValueError("请选择有效的活动地区。")
        if not name:
            raise ValueError("活动名称不能为空。")
        event = AttendanceEvent(
            name=name, event_date=event_date, location=location, organizer=organizer, activity_region=activity_region,
            is_manual=True, created_by_id=current_user.id
        )
        db.session.add(event)
        db.session.commit()
        flash("出勤活动记录已增加，可以在表格中手动调整出勤。", "success")
    except Exception as exc:
        db.session.rollback()
        flash(str(exc), "error")
    return redirect(url_for("attendance"))


@app.route("/attendance/events/<int:event_id>/edit", methods=["POST"])
@login_required
@attendance_manage_required
def edit_attendance_event(event_id):
    event = db.session.get(AttendanceEvent, event_id)
    if not event:
        flash("历史活动不存在。", "error")
        return redirect(url_for("attendance"))
    try:
        name = request.form.get("name", "").strip()
        event_date = datetime.strptime(request.form.get("event_date", ""), "%Y-%m-%d").date()
        location = request.form.get("location", "").strip()
        organizer = request.form.get("organizer", "").strip()
        activity_region = request.form.get("activity_region", "宁波").strip() or "宁波"
        if activity_region not in ACTIVITY_REGION_OPTIONS:
            raise ValueError("请选择有效的活动地区。")
        if not name:
            raise ValueError("活动名称不能为空。")
        event.name = name
        event.event_date = event_date
        event.location = location
        event.organizer = organizer
        event.activity_region = activity_region
        db.session.commit()
        flash("出勤活动记录已更新。", "success")
    except Exception as exc:
        db.session.rollback()
        flash(str(exc), "error")
    return redirect(request.referrer or url_for("attendance"))


@app.route("/attendance/events/<int:event_id>/delete", methods=["POST"])
@login_required
@attendance_manage_required
def delete_attendance_event(event_id):
    event = db.session.get(AttendanceEvent, event_id)
    if not event:
        flash("历史活动不存在。", "error")
        return redirect(url_for("attendance"))
    source_activity = event.source_activity
    if source_activity:
        # 删除自动生成的出勤记录时，关闭该活动的自动出勤同步，避免下次进入页面又被重新生成。
        source_activity.attendance_enabled = False
    db.session.delete(event)
    db.session.commit()
    flash("出勤活动记录已删除。", "success")
    return redirect(request.referrer or url_for("attendance"))


@app.route("/attendance/<int:event_id>/<int:user_id>/toggle", methods=["POST"])
@login_required
@attendance_manage_required
def toggle_attendance_record(event_id, user_id):
    event = db.session.get(AttendanceEvent, event_id)
    user = db.session.get(User, user_id)
    if not event or not user:
        flash("出勤记录或用户不存在。", "error")
        return redirect(url_for("attendance"))
    record = AttendanceRecord.query.filter_by(event_id=event.id, user_id=user.id).first()
    new_present = not bool(record and record.present)
    set_attendance_record(event.id, user.id, new_present)
    db.session.commit()

    # V36: support AJAX toggling so the attendance table does not jump back to the top.
    if request.headers.get("X-Requested-With") == "XMLHttpRequest" or request.accept_mimetypes.best == "application/json":
        count = AttendanceRecord.query.filter_by(user_id=user.id, present=True).join(AttendanceEvent, AttendanceRecord.event_id == AttendanceEvent.id)
        selected_year = request.args.get("year") or request.form.get("year")
        selected_region = request.args.get("region") or request.form.get("region")
        if selected_year and selected_year.isdigit():
            year = int(selected_year)
            count = count.filter(AttendanceEvent.event_date >= datetime(year, 1, 1).date(), AttendanceEvent.event_date < datetime(year + 1, 1, 1).date())
        if selected_region in ACTIVITY_REGION_OPTIONS:
            count = count.filter(AttendanceEvent.activity_region == selected_region)
        return jsonify({"ok": True, "present": new_present, "user_id": user.id, "count": count.count()})

    flash("出勤记录已更新。", "success")
    return redirect(request.referrer or url_for("attendance"))


@app.route("/admin/presets")
@login_required
@admin_required
def admin_presets():
    # 保留旧入口，自动跳转到场地管理。
    return redirect(url_for("venue_management"))


@app.route("/admin/venues")
@login_required
@admin_required
def venue_management():
    venues = Venue.query.order_by(Venue.created_at.desc()).all()
    return render_template("venues.html", venues=venues)


@app.route("/admin/game-modes")
@login_required
@admin_required
def mode_management():
    game_modes = GameMode.query.order_by(GameMode.created_at.desc()).all()
    return render_template("game_modes.html", game_modes=game_modes)


@app.route("/venues/create", methods=["POST"])
@login_required
@admin_required
def create_venue():
    name = request.form.get("name", "").strip()
    address = request.form.get("address", "").strip()
    if not name or not address:
        flash("场地名称和地址不能为空。", "error")
    elif Venue.query.filter_by(name=name).first():
        flash("场地名称已存在。", "error")
    else:
        db.session.add(Venue(name=name, address=address, created_by_id=current_user.id))
        db.session.commit()
        flash("预设场地已创建。", "success")
    return redirect(url_for("venue_management"))


@app.route("/venues/<int:venue_id>/update", methods=["POST"])
@login_required
@admin_required
def update_venue(venue_id):
    venue = db.session.get(Venue, venue_id)
    if not venue:
        flash("场地不存在。", "error")
        return redirect(url_for("venue_management"))
    name = request.form.get("name", "").strip()
    address = request.form.get("address", "").strip()
    duplicate = Venue.query.filter(Venue.name == name, Venue.id != venue.id).first() if name else None
    if not name or not address:
        flash("场地名称和地址不能为空。", "error")
    elif duplicate:
        flash("场地名称已存在。", "error")
    else:
        venue.name = name
        venue.address = address
        db.session.commit()
        flash("预设场地已修改。", "success")
    return redirect(url_for("venue_management"))


@app.route("/game-modes/create", methods=["POST"])
@login_required
@admin_required
def create_game_mode():
    name = request.form.get("name", "").strip()
    suitable_people = request.form.get("suitable_people", "").strip()
    rules = request.form.get("rules", "").strip()
    if not name or not suitable_people or not rules:
        flash("模式名称、适合人数和玩法都不能为空。", "error")
    elif GameMode.query.filter_by(name=name).first():
        flash("游戏模式名称已存在。", "error")
    else:
        db.session.add(GameMode(name=name, suitable_people=suitable_people, rules=rules, created_by_id=current_user.id))
        db.session.commit()
        flash("预设游戏模式已创建。", "success")
    return redirect(url_for("mode_management"))


@app.route("/game-modes/<int:mode_id>/update", methods=["POST"])
@login_required
@admin_required
def update_game_mode(mode_id):
    mode = db.session.get(GameMode, mode_id)
    if not mode:
        flash("游戏模式不存在。", "error")
        return redirect(url_for("mode_management"))
    name = request.form.get("name", "").strip()
    suitable_people = request.form.get("suitable_people", "").strip()
    rules = request.form.get("rules", "").strip()
    duplicate = GameMode.query.filter(GameMode.name == name, GameMode.id != mode.id).first() if name else None
    if not name or not suitable_people or not rules:
        flash("模式名称、适合人数和玩法都不能为空。", "error")
    elif duplicate:
        flash("游戏模式名称已存在。", "error")
    else:
        mode.name = name
        mode.suitable_people = suitable_people
        mode.rules = rules
        db.session.commit()
        flash("预设游戏模式已修改。", "success")
    return redirect(url_for("mode_management"))




@app.route("/activities/<int:activity_id>/launcher-options", methods=["POST"])
@login_required
@admin_required
def update_activity_launcher_options(activity_id):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        flash("活动不存在。", "error")
        return redirect(url_for("dashboard"))
    try:
        selected_ids = request.form.getlist("launcher_ids")
        replace_activity_launcher_options(activity.id, selected_ids)

        allowed_ids = activity_allowed_launcher_ids(activity.id)
        now = datetime.now()
        # 如果管理员关闭了某个发射器对本活动开放，取消本活动中对应的未取消租赁。
        for rental in ActivityLauncherRental.query.filter_by(activity_id=activity.id, cancelled_at=None).all():
            if rental.launcher_id not in allowed_ids:
                rental.cancelled_at = now
        db.session.commit()
        flash("本活动开放租赁发射器已更新。", "success")
    except Exception as exc:
        db.session.rollback()
        flash(f"更新失败：{exc}", "error")
    return redirect(url_for("activity_detail", activity_id=activity.id))


@app.route("/admin/launchers")
@login_required
@admin_required
def launcher_management():
    launchers = LauncherRentalItem.query.order_by(LauncherRentalItem.created_at.desc()).all()
    setting = get_launcher_rental_setting()
    db.session.commit()
    return render_template("launchers.html", launchers=launchers, setting=setting)


@app.route("/launchers/settings", methods=["POST"])
@login_required
@admin_required
def update_launcher_setting():
    setting = get_launcher_rental_setting()
    setting.note = request.form.get("note", "").strip()
    setting.updated_at = datetime.now()
    db.session.commit()
    flash("发射器租赁说明已保存。", "success")
    return redirect(url_for("launcher_management"))


@app.route("/launchers/create", methods=["POST"])
@login_required
@admin_required
def create_launcher():
    try:
        owner_type = request.form.get("owner_type", "").strip()
        name = request.form.get("name", "").strip()
        description = request.form.get("description", "").strip()
        rent_fee = request.form.get("rent_fee", "").strip()
        if not owner_type:
            raise ValueError("发射器所有人不能为空。")
        if not name:
            raise ValueError("发射器名称不能为空。")
        if len(description) > 50:
            raise ValueError("发射器说明不能超过 50 字。")
        if not rent_fee:
            rent_fee = "0"
        item = LauncherRentalItem(owner_type=owner_type, name=name, description=description, rent_fee=rent_fee, photo_filename=None, created_by_id=current_user.id)
        db.session.add(item)
        db.session.flush()
        photo_filename = save_launcher_photo(request.files.get("photo"), item.id)
        if photo_filename:
            item.photo_filename = photo_filename
        db.session.commit()
        flash("租赁发射器已创建。", "success")
    except Exception as exc:
        db.session.rollback()
        flash(str(exc), "error")
    return redirect(url_for("launcher_management"))


@app.route("/launchers/<int:launcher_id>/update", methods=["POST"])
@login_required
@admin_required
def update_launcher(launcher_id):
    item = db.session.get(LauncherRentalItem, launcher_id)
    if not item:
        flash("发射器不存在。", "error")
        return redirect(url_for("launcher_management"))
    try:
        owner_type = request.form.get("owner_type", "").strip()
        name = request.form.get("name", "").strip()
        description = request.form.get("description", "").strip()
        rent_fee = request.form.get("rent_fee", "").strip()
        active = request.form.get("active") == "1"
        if not owner_type:
            raise ValueError("发射器所有人不能为空。")
        if not name:
            raise ValueError("发射器名称不能为空。")
        if len(description) > 50:
            raise ValueError("发射器说明不能超过 50 字。")
        item.owner_type = owner_type
        item.name = name
        item.description = description
        item.rent_fee = rent_fee or "0"
        item.active = active
        photo_filename = save_launcher_photo(request.files.get("photo"), item.id)
        if photo_filename:
            item.photo_filename = photo_filename
        item.updated_at = datetime.now()
        db.session.commit()
        flash("租赁发射器已修改。", "success")
    except Exception as exc:
        db.session.rollback()
        flash(str(exc), "error")
    return redirect(url_for("launcher_management"))


@app.route("/launchers/<int:launcher_id>/delete", methods=["POST"])
@login_required
@superadmin_required
def delete_launcher(launcher_id):
    item = db.session.get(LauncherRentalItem, launcher_id)
    if not item:
        flash("发射器不存在。", "error")
        return redirect(url_for("launcher_management"))
    if ActivityLauncherRental.query.filter_by(launcher_id=item.id, cancelled_at=None).first():
        flash("该发射器已有租赁记录，不能删除。可以改为停用。", "error")
        return redirect(url_for("launcher_management"))
    db.session.delete(item)
    db.session.commit()
    flash("发射器已删除。", "success")
    return redirect(url_for("launcher_management"))


@app.route("/plans/create", methods=["POST"])
@login_required
@admin_required
def create_plan():
    try:
        name = request.form.get("name", "").strip()
        raw_date_values = request.form.getlist("dates")
        raw_note_values = request.form.getlist("date_notes")
        raw_dates = [x.strip() for x in raw_date_values if x and x.strip()]
        date_notes = {}
        if len(raw_dates) == 1 and ("," in raw_dates[0] or "，" in raw_dates[0]):
            dates = parse_date_list(raw_dates[0])
        else:
            dates = []
            for index, raw_value in enumerate(raw_date_values):
                raw_date = (raw_value or "").strip()
                if not raw_date:
                    continue
                parsed_dates = parse_date_list(raw_date)
                note = (raw_note_values[index] if index < len(raw_note_values) else "").strip()
                note = note[:80]
                for parsed_date in parsed_dates:
                    dates.append(parsed_date)
                    if note and parsed_date not in date_notes:
                        date_notes[parsed_date] = note
        # 去重并保持顺序
        seen_dates = set()
        dates = [d for d in dates if not (d in seen_dates or seen_dates.add(d))]
        venue_ids = [int(x) for x in request.form.getlist("venue_ids") if x]
        mode_ids = [int(x) for x in request.form.getlist("mode_ids") if x]
        vote_deadline = parse_datetime_local(request.form.get("vote_deadline", ""))
        if not name:
            raise ValueError("活动名称不能为空。")
        if not dates:
            raise ValueError("至少填写一个可选日期，格式为 YYYY-MM-DD。")
        if not venue_ids:
            raise ValueError("至少选择一个可选场地。")
        if not mode_ids:
            raise ValueError("至少选择一个游戏模式。")
        visibility_type, invitee_ids = parse_activity_visibility_from_form()
        plan = ActivityPlan(name=name, vote_deadline=vote_deadline, visibility_type=visibility_type, invitee_ids=invitee_ids, created_by_id=current_user.id)
        db.session.add(plan)
        db.session.flush()
        for d in dates:
            db.session.add(PlanDateOption(plan_id=plan.id, date=d, note=date_notes.get(d)))
        for vid in venue_ids:
            if db.session.get(Venue, vid):
                db.session.add(PlanVenueOption(plan_id=plan.id, venue_id=vid))
        for mid in mode_ids:
            if db.session.get(GameMode, mid):
                db.session.add(PlanGameModeOption(plan_id=plan.id, game_mode_id=mid))
        db.session.commit()
        flash("活动策划已发起。", "success")
        return redirect(url_for("plan_detail", plan_id=plan.id))
    except Exception as exc:
        db.session.rollback()
        flash(str(exc), "error")
        return redirect(url_for("dashboard"))


@app.route("/plans/<int:plan_id>")
@login_required
def plan_detail(plan_id):
    plan = db.session.get(ActivityPlan, plan_id)
    if not plan or (plan.hidden and not current_user.is_admin) or (not current_user.is_admin and not can_view_plan(plan)):
        flash("活动策划不存在、已隐藏或你没有查看权限。", "error")
        return redirect(url_for("dashboard"))
    stats = plan_vote_stats(plan)
    my_votes = user_plan_votes(plan.id, current_user.id)
    vote_details = plan_vote_details(plan)
    voters_count = db.session.query(PlanVote.user_id).filter_by(plan_id=plan.id).distinct().count()
    status_text, status_class = plan_status(plan)
    launchers = LauncherRentalItem.query.filter_by(active=True).order_by(LauncherRentalItem.name.asc()).all()
    return render_template(
        "plan.html",
        plan=plan,
        stats=stats,
        my_votes=my_votes,
        vote_details=vote_details,
        voters_count=voters_count,
        status_text=status_text,
        status_class=status_class,
        launchers=launchers,
        users=User.query.filter_by(disabled=False).order_by(User.callsign.asc()).all(),
        visibility_label=plan_visibility_label(plan),
        invited_users=get_plan_invited_users(plan),
        invited_plan_ids=set(parse_invitee_ids(getattr(plan, "invitee_ids", "") or "")),
        can_view_visibility_info=can_view_activity_visibility_info(plan),
    )


@app.route("/plans/<int:plan_id>/edit", methods=["POST"])
@login_required
@admin_required
def edit_plan(plan_id):
    plan = db.session.get(ActivityPlan, plan_id)
    if not plan:
        flash("活动策划不存在。", "error")
        return redirect(url_for("dashboard"))
    try:
        name = request.form.get("name", "").strip()
        vote_deadline = parse_datetime_local(request.form.get("vote_deadline", ""))
        if not name:
            raise ValueError("活动名称不能为空。")
        visibility_type, invitee_ids = parse_activity_visibility_from_form()
        plan.name = name
        plan.vote_deadline = vote_deadline
        plan.visibility_type = visibility_type
        plan.invitee_ids = invitee_ids
        db.session.commit()
        flash("活动策划已更新。", "success")
    except Exception as exc:
        db.session.rollback()
        flash(str(exc), "error")
    return redirect(url_for("plan_detail", plan_id=plan.id))


@app.route("/plans/<int:plan_id>/vote", methods=["POST"])
@login_required
def vote_plan(plan_id):
    plan = db.session.get(ActivityPlan, plan_id)
    if not plan or plan.hidden:
        flash("活动策划不存在或已隐藏。", "error")
        return redirect(url_for("dashboard"))
    if datetime.now() > plan.vote_deadline:
        flash("投票已截止。", "error")
        return redirect(url_for("plan_detail", plan_id=plan.id))
    PlanVote.query.filter_by(plan_id=plan.id, user_id=current_user.id).delete()
    allowed_date_ids = {o.id for o in plan.date_options}
    allowed_venue_ids = {o.venue_id for o in plan.venue_options}
    for oid in request.form.getlist("date_ids"):
        oid = int(oid)
        if oid in allowed_date_ids:
            db.session.add(PlanVote(plan_id=plan.id, user_id=current_user.id, option_type="date", option_id=oid))
    for oid in request.form.getlist("venue_ids"):
        oid = int(oid)
        if oid in allowed_venue_ids:
            db.session.add(PlanVote(plan_id=plan.id, user_id=current_user.id, option_type="venue", option_id=oid))
    db.session.commit()
    flash("投票已保存。", "success")
    return redirect(url_for("plan_detail", plan_id=plan.id))


@app.route("/plans/<int:plan_id>/convert", methods=["POST"])
@login_required
@admin_required
def convert_plan_to_activity(plan_id):
    plan = db.session.get(ActivityPlan, plan_id)
    if not plan:
        flash("活动策划不存在。", "error")
        return redirect(url_for("dashboard"))
    try:
        date_option_id = int(request.form.get("date_option_id", "0"))
        venue_id = int(request.form.get("venue_id", "0"))
        start_time = request.form.get("start_time", "09:00")
        end_time = request.form.get("end_time", "17:00")
        date_option = db.session.get(PlanDateOption, date_option_id)
        venue = db.session.get(Venue, venue_id)
        if not date_option or date_option.plan_id != plan.id:
            raise ValueError("请选择该策划中的可选日期。")
        if not venue or venue_id not in {o.venue_id for o in plan.venue_options}:
            raise ValueError("请选择该策划中的开放场地。")
        start_at = datetime.strptime(f"{date_option.date}T{start_time}", "%Y-%m-%dT%H:%M")
        end_at = datetime.strptime(f"{date_option.date}T{end_time}", "%Y-%m-%dT%H:%M")
        if end_at <= start_at:
            raise ValueError("活动结束时间必须晚于开始时间。")
        open_min = int(request.form.get("open_min", "0"))
        camp_count = int(request.form.get("camp_count", "0"))
        camp_limit = int(request.form.get("camp_limit", "0"))
        squad_count = int(request.form.get("squad_count", "0"))
        squad_limit = int(request.form.get("squad_limit", "0"))
        allowed_jobs = request.form.getlist("allowed_jobs")
        launcher_ids = request.form.getlist("launcher_ids")
        activity_region = request.form.get("activity_region", "宁波").strip() or "宁波"
        if activity_region not in ACTIVITY_REGION_OPTIONS:
            raise ValueError("请选择有效的活动地区。")
        visibility_type, invitee_ids = parse_activity_visibility_from_form()
        mode_names = []
        allowed_plan_modes = {o.game_mode_id for o in plan.mode_options}
        for mid in request.form.getlist("mode_ids"):
            mid = int(mid)
            mode = db.session.get(GameMode, mid)
            if mode and mid in allowed_plan_modes:
                mode_names.append(mode.name)
        if min(open_min, camp_count, camp_limit, squad_count, squad_limit) <= 0:
            raise ValueError("人数和数量必须大于 0。")
        if not allowed_jobs:
            raise ValueError("至少选择一个开放职业。")
        if not mode_names:
            raise ValueError("至少选择一个游戏模式。")
        activity = Activity(
            name=plan.name, start_at=start_at, end_at=end_at, location=venue.name,
            open_min=open_min, camp_count=camp_count, camp_limit=camp_limit, squad_count=squad_count, squad_limit=squad_limit,
            allowed_jobs=",".join(allowed_jobs), game_modes=",".join(mode_names), attendance_enabled=True, activity_region=activity_region, visibility_type=visibility_type, invitee_ids=invitee_ids, created_by_id=current_user.id
        )
        db.session.add(activity)
        db.session.flush()
        ensure_activity_settings(activity)
        replace_activity_launcher_options(activity.id, launcher_ids)
        plan.hidden = True
        plan.converted_activity_id = activity.id
        db.session.commit()
        flash("正式活动已创建，原活动策划已隐藏。", "success")
        return redirect(url_for("activity_detail", activity_id=activity.id))
    except Exception as exc:
        db.session.rollback()
        flash(str(exc), "error")
        return redirect(url_for("plan_detail", plan_id=plan.id))


@app.route("/activity-management")
@login_required
@admin_required
def activity_management():
    try:
        sync_attendance_events()
    except Exception:
        db.session.rollback()
    activities = Activity.query.order_by(Activity.start_at.desc()).all()
    plans = ActivityPlan.query.order_by(ActivityPlan.created_at.desc()).all()
    return render_template("activity_management.html", activities=activities, plans=plans, activity_status=activity_status, plan_status=plan_status, enrollment_count=enrollment_count)


@app.route("/activities/<int:activity_id>/delete", methods=["POST"])
@login_required
@admin_required
def delete_activity(activity_id):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        flash("活动不存在。", "error")
        return redirect(url_for("activity_management"))
    try:
        if activity.deleted_at:
            flash("该活动已经取消。", "warning")
        else:
            activity.deleted_at = datetime.now()
            activity.deleted_by_id = current_user.id
            db.session.commit()
            attendance_event = AttendanceEvent.query.filter_by(source_activity_id=activity.id).first()
            if attendance_event:
                db.session.delete(attendance_event)
            db.session.commit()
            flash(f"活动《{activity.name}》已取消，并保留在活动管理记录中。", "success")
    except Exception as exc:
        db.session.rollback()
        flash(f"取消活动失败：{exc}", "error")
    return redirect(url_for("activity_management"))


@app.route("/activities/<int:activity_id>/hard-delete", methods=["POST"])
@login_required
@superadmin_required
def hard_delete_activity(activity_id):
    activity = db.session.get(Activity, activity_id)
    if not activity:
        flash("活动不存在。", "error")
        return redirect(url_for("activity_management"))
    try:
        name = activity.name
        # 只有超级管理员可以手动彻底删除活动记录。
        attendance_event = AttendanceEvent.query.filter_by(source_activity_id=activity.id).first()
        if attendance_event:
            db.session.delete(attendance_event)
        Enrollment.query.filter_by(activity_id=activity.id).delete(synchronize_session=False)
        ActivityLauncherRental.query.filter_by(activity_id=activity.id).delete(synchronize_session=False)
        ActivityLauncherOption.query.filter_by(activity_id=activity.id).delete(synchronize_session=False)
        CampSetting.query.filter_by(activity_id=activity.id).delete(synchronize_session=False)
        SquadSetting.query.filter_by(activity_id=activity.id).delete(synchronize_session=False)
        ActivityPlan.query.filter_by(converted_activity_id=activity.id).update({"converted_activity_id": None}, synchronize_session=False)
        db.session.delete(activity)
        db.session.commit()
        flash(f"活动记录《{name}》已彻底删除。", "success")
    except Exception as exc:
        db.session.rollback()
        flash(f"彻底删除活动记录失败：{exc}", "error")
    return redirect(url_for("activity_management"))


@app.route("/plans/<int:plan_id>/delete", methods=["POST"])
@login_required
@superadmin_required
def delete_plan(plan_id):
    plan = db.session.get(ActivityPlan, plan_id)
    if not plan:
        flash("活动策划不存在。", "error")
        return redirect(url_for("activity_management"))
    try:
        name = plan.name
        # PlanDateOption / PlanVenueOption / PlanGameModeOption / PlanVote 已配置级联删除。
        db.session.delete(plan)
        db.session.commit()
        flash(f"活动策划《{name}》已删除。", "success")
    except Exception as exc:
        db.session.rollback()
        flash(f"删除活动策划失败：{exc}", "error")
    return redirect(url_for("activity_management"))


@app.route("/plans/<int:plan_id>/toggle-hidden", methods=["POST"])
@login_required
@admin_required
def toggle_plan_hidden(plan_id):
    plan = db.session.get(ActivityPlan, plan_id)
    if not plan:
        flash("活动策划不存在。", "error")
    else:
        plan.hidden = not plan.hidden
        db.session.commit()
        flash("活动策划显示状态已更新。", "success")
    return redirect(url_for("activity_management"))


@app.route("/users/<int:user_id>/toggle-extraction-authorized", methods=["POST"])
@login_required
@extraction_manage_required
def toggle_extraction_authorized(user_id):
    user = db.session.get(User, user_id)
    if not user:
        flash("用户不存在。", "error")
    elif user.is_superadmin and not current_user.is_superadmin:
        flash("不能修改超级管理员。", "error")
    else:
        user.extraction_authorized = not bool(user.extraction_authorized)
        db.session.commit()
        flash("逃离西撇镇授权已更新。", "success")
    return redirect(url_for("users"))


@app.route("/users/<int:user_id>/toggle-extraction-manager", methods=["POST"])
@login_required
@superadmin_required
def toggle_extraction_manager(user_id):
    user = db.session.get(User, user_id)
    if not user:
        flash("用户不存在。", "error")
    elif user.id == current_user.id:
        flash("不能修改自己的逃离西撇镇管理员权限。", "error")
    else:
        user.extraction_manager = not bool(user.extraction_manager)
        if user.extraction_manager:
            user.extraction_authorized = True
        db.session.commit()
        flash("逃离西撇镇管理员权限已更新。", "success")
    return redirect(url_for("users"))


@app.route("/users/<int:user_id>/manager-permissions", methods=["POST"])
@login_required
@superadmin_required
def update_user_manager_permissions(user_id):
    user = db.session.get(User, user_id)
    if not user:
        flash("用户不存在。", "error")
    elif user.id == current_user.id:
        flash("不能修改自己的管理员权限。", "error")
    else:
        user.attendance_manager = "attendance_manager" in request.form.getlist("manager_permissions")
        user.extraction_manager = "extraction_manager" in request.form.getlist("manager_permissions")
        if user.extraction_manager:
            user.extraction_authorized = True
        db.session.commit()
        flash("管理员权限已更新。", "success")
    return redirect(url_for("users"))


@app.route("/extraction")
@login_required
@extraction_access_required
def extraction_home():
    sync_extraction_runtime()
    profile = get_extraction_profile(current_user.id)
    season = current_extraction_season()
    match_stats = get_user_extraction_match_stats(current_user.id, season)
    profile.runs_count = int(match_stats["match_count"] or 0)
    season_items = ExtractionInventoryItem.query.filter_by(user_id=current_user.id, season_id=season.id if season else None).all() if season else []
    levels = ["超凡", "史诗", "精品", "普通"]
    level_counts = {level: 0 for level in levels}
    season_cash = 0
    for it in season_items:
        if it.item_def.level in level_counts:
            level_counts[it.item_def.level] += 1
        if it.sold_price:
            season_cash += it.sold_price
    normalize_extraction_inventory_layout(current_user.id, "buffer", 12, 30)
    normalize_extraction_inventory_layout(current_user.id, "storage", profile.storage_rows, profile.storage_cols)
    buffer_items = ExtractionInventoryItem.query.filter_by(user_id=current_user.id, location="buffer", sold_at=None).order_by(ExtractionInventoryItem.row.asc(), ExtractionInventoryItem.col.asc(), ExtractionInventoryItem.created_at.desc()).all()
    storage_items = ExtractionInventoryItem.query.filter_by(user_id=current_user.id, location="storage", sold_at=None).order_by(ExtractionInventoryItem.row.asc(), ExtractionInventoryItem.col.asc(), ExtractionInventoryItem.created_at.desc()).all()
    prices = {it.item_def_id: extraction_today_price(it.item_def) for it in buffer_items + storage_items}
    trends = {it.item_def_id: extraction_trend(it.item_def) for it in buffer_items + storage_items}
    return render_template("extraction_home.html", profile=profile, season=season, match_stats=match_stats, level_counts=level_counts, season_cash=season_cash, buffer_items=buffer_items, storage_items=storage_items, prices=prices, trends=trends, buffer_rows=12, buffer_cols=30)


@app.route("/extraction/exit")
@login_required
def extraction_exit():
    return redirect(url_for("dashboard"))


@app.route("/extraction/inventory/<int:item_id>/sell", methods=["POST"])
@login_required
@extraction_access_required
def extraction_sell_item(item_id):
    inv = db.session.get(ExtractionInventoryItem, item_id)
    if not inv or inv.user_id != current_user.id or inv.sold_at:
        flash("物品不存在或已出售。", "error")
        return redirect(url_for("extraction_home"))
    price = sell_extraction_inventory_item(inv)
    flash(f"已出售 {inv.item_def.name}，获得 {price} 现金。", "success")
    return redirect(url_for("extraction_home"))


@app.route("/extraction/inventory/sell-many", methods=["POST"])
@login_required
@extraction_access_required
def extraction_sell_many():
    ids = request.form.getlist("item_ids")
    total = 0
    count = 0
    for raw in ids:
        try:
            item_id = int(raw)
        except (TypeError, ValueError):
            continue
        inv = db.session.get(ExtractionInventoryItem, item_id)
        if not inv or inv.user_id != current_user.id or inv.sold_at:
            continue
        total += sell_extraction_inventory_item(inv)
        count += 1
    if count:
        flash(f"已出售 {count} 个物品，获得 {total} 现金。", "success")
    else:
        flash("没有可出售的物品。", "error")
    return redirect(url_for("extraction_home"))


@app.route("/extraction/inventory/<int:item_id>/place", methods=["POST"])
@login_required
@extraction_access_required
def extraction_place_item(item_id):
    inv = db.session.get(ExtractionInventoryItem, item_id)
    if not inv or inv.user_id != current_user.id or inv.sold_at:
        return jsonify({"ok": False, "message": "物品不存在或已出售。"}), 404
    data = request.get_json(silent=True) or request.form
    location = (data.get("location") or "").strip()
    try:
        row = int(data.get("row"))
        col = int(data.get("col"))
    except (TypeError, ValueError):
        return jsonify({"ok": False, "message": "位置无效。"}), 400
    profile = get_extraction_profile(current_user.id)
    if location == "buffer":
        rows, cols = 12, 30
    elif location == "storage":
        rows, cols = profile.storage_rows, profile.storage_cols
    else:
        return jsonify({"ok": False, "message": "仓库类型无效。"}), 400
    # V51: Only allow buffer -> storage, storage -> storage, and same-location rearrange.
    if inv.location == "storage" and location == "buffer":
        return jsonify({"ok": False, "message": "个人仓库物品不能放回缓冲区。"}), 400
    if not can_place_extraction_item(current_user.id, location, rows, cols, inv.item_def.width, inv.item_def.height, row, col, exclude_item_id=inv.id):
        return jsonify({"ok": False, "message": "目标位置空间不足或发生重叠。"}), 400
    inv.location = location
    inv.row = row
    inv.col = col
    db.session.commit()
    return jsonify({"ok": True, "message": "已移动。", "location": location, "row": row, "col": col})


@app.route("/extraction/inventory/<int:item_id>/move-storage", methods=["POST"])
@login_required
@extraction_access_required
def extraction_move_to_storage(item_id):
    inv = db.session.get(ExtractionInventoryItem, item_id)
    if not inv or inv.user_id != current_user.id or inv.sold_at or inv.location != "buffer":
        flash("物品不能移动。", "error")
        return redirect(url_for("extraction_home"))
    profile = get_extraction_profile(current_user.id)
    row, col = first_free_slot(current_user.id, "storage", profile.storage_rows, profile.storage_cols, inv.item_def.width, inv.item_def.height)
    if row is None:
        flash("个人仓库空间不足。", "error")
    else:
        inv.location = "storage"
        inv.row = row
        inv.col = col
        db.session.commit()
        flash("已转入个人仓库。", "success")
    return redirect(url_for("extraction_home"))


@app.route("/extraction/entry", methods=["GET", "POST"])
@login_required
@extraction_manage_required
def extraction_entry():
    sync_extraction_runtime()
    users = User.query.filter(User.disabled.is_(False)).order_by(User.callsign.asc()).all()
    users = [u for u in users if u.can_access_extraction]
    item_defs = ExtractionItemDef.query.filter_by(active=True).order_by(ExtractionItemDef.level.asc(), ExtractionItemDef.name.asc()).all()
    season = current_extraction_season()
    if request.method == "POST":
        try:
            user_id = int(request.form.get("user_id"))
            item_def_id = int(request.form.get("item_def_id"))
            qty = max(1, min(int(request.form.get("qty", "1")), 20))
        except Exception:
            flash("请选择用户和物品。", "error")
            return redirect(url_for("extraction_entry"))
        user = db.session.get(User, user_id)
        item_def = db.session.get(ExtractionItemDef, item_def_id)
        if not user or not item_def:
            flash("用户或物品不存在。", "error")
        else:
            for _ in range(qty):
                db.session.add(ExtractionInventoryItem(user_id=user.id, item_def_id=item_def.id, season_id=season.id if season else None, location="buffer"))
            db.session.commit()
            flash("物品已录入到用户缓冲区仓库。", "success")
        return redirect(url_for("extraction_entry", user_id=user_id))
    selected_user_id = request.args.get("user_id", type=int)
    return render_template("extraction_entry.html", users=users, item_defs=item_defs, season=season, selected_user_id=selected_user_id)


@app.route("/extraction/items", methods=["GET", "POST"])
@login_required
@extraction_manage_required
def extraction_items():
    if request.method == "POST":
        name = request.form.get("name", "").strip()
        level = request.form.get("level", "普通").strip()
        if level not in {"超凡", "史诗", "精品", "普通"}:
            level = "普通"
        item_category = request.form.get("item_category", "常规物品").strip() or "常规物品"
        if item_category not in {"常规物品", "武器"}:
            item_category = "常规物品"
        try:
            min_price = max(1, int(request.form.get("min_price", "1")))
            max_price = max(min_price, int(request.form.get("max_price", str(min_price))))
            width = max(1, min(int(request.form.get("width", "1")), 30))
            height = max(1, min(int(request.form.get("height", "1")), 12))
        except ValueError:
            flash("价格和体积必须是正整数。", "error")
            return redirect(url_for("extraction_items"))
        if not name:
            flash("物品名称不能为空。", "error")
            return redirect(url_for("extraction_items"))
        photo = request.files.get("photo")
        item = ExtractionItemDef(name=name, level=level, item_category=item_category, min_price=min_price, max_price=max_price, width=width, height=height, photo_filename=None, created_by_id=current_user.id)
        db.session.add(item)
        db.session.flush()
        if photo and photo.filename:
            try:
                item.photo_filename = save_uploaded_photo(photo, "extraction_items", f"item_{item.id}")
            except ValueError as exc:
                flash(str(exc), "error")
                db.session.rollback()
                return redirect(url_for("extraction_items"))
        db.session.commit()
        flash("物品已创建。", "success")
        return redirect(url_for("extraction_items"))
    item_defs = ExtractionItemDef.query.order_by(ExtractionItemDef.created_at.desc()).all()
    today_prices = {item.id: extraction_today_price(item) for item in item_defs if item.active}
    return render_template("extraction_items.html", item_defs=item_defs, today_prices=today_prices)


@app.route("/extraction/items/<int:item_id>/delete", methods=["POST"])
@login_required
@extraction_manage_required
def extraction_delete_item(item_id):
    item = db.session.get(ExtractionItemDef, item_id)
    if not item:
        flash("物品不存在。", "error")
        return redirect(url_for("extraction_items"))
    try:
        name = item.name
        # 删除所有用户仓库中该物品，以及每日价格记录；商店历史绑定清空，避免外键引用。
        ExtractionInventoryItem.query.filter_by(item_def_id=item.id).delete(synchronize_session=False)
        ExtractionItemPrice.query.filter_by(item_def_id=item.id).delete(synchronize_session=False)
        for shop_item in ExtractionShopItem.query.filter_by(item_def_id=item.id).all():
            shop_item.item_def_id = None
        db.session.delete(item)
        db.session.commit()
        flash(f"物品《{name}》已删除，并已从所有用户仓库中移除。", "success")
    except Exception as exc:
        db.session.rollback()
        flash(f"删除物品失败：{exc}", "error")
    return redirect(url_for("extraction_items"))


@app.route("/extraction/assets")
@login_required
@superadmin_required
def extraction_assets():
    sync_extraction_runtime()
    users = User.query.filter(User.disabled.is_(False)).order_by(User.callsign.asc()).all()
    selected_user_id = request.args.get("user_id", type=int)
    selected_user = db.session.get(User, selected_user_id) if selected_user_id else (users[0] if users else None)
    profile = None
    season = current_extraction_season()
    level_counts = {level: 0 for level in ["超凡", "史诗", "精品", "普通"]}
    season_cash = 0
    storage_items = []
    prices = {}
    trends = {}
    if selected_user:
        profile = get_extraction_profile(selected_user.id)
        if season:
            season_items = ExtractionInventoryItem.query.filter_by(user_id=selected_user.id, season_id=season.id).all()
            for it in season_items:
                if it.item_def.level in level_counts:
                    level_counts[it.item_def.level] += 1
                if it.sold_price:
                    season_cash += it.sold_price
        normalize_extraction_inventory_layout(selected_user.id, "storage", profile.storage_rows, profile.storage_cols)
        storage_items = ExtractionInventoryItem.query.filter_by(user_id=selected_user.id, location="storage", sold_at=None).order_by(ExtractionInventoryItem.row.asc(), ExtractionInventoryItem.col.asc()).all()
        prices = {it.item_def_id: extraction_today_price(it.item_def) for it in storage_items}
        trends = {it.item_def_id: extraction_trend(it.item_def) for it in storage_items}
    return render_template("extraction_assets.html", users=users, selected_user=selected_user, profile=profile, season=season, level_counts=level_counts, season_cash=season_cash, storage_items=storage_items, prices=prices, trends=trends)


def _create_extraction_shop_item_from_form():
    category = request.form.get("category", "storage")
    name = request.form.get("name", "").strip()
    try:
        price = max(0, int(request.form.get("price", "0")))
        stock = max(0, int(request.form.get("stock", "0")))
    except ValueError:
        flash("价格和库存必须是整数。", "error")
        return False
    shelf_until_raw = request.form.get("shelf_until", "").strip()
    shelf_until = datetime.strptime(shelf_until_raw, "%Y-%m-%d").date() if shelf_until_raw else None
    if category == "storage":
        rows = max(1, int(request.form.get("storage_rows", "6")))
        cols = max(1, int(request.form.get("storage_cols", "10")))
        item_def_id = None
    elif category in ["item", "weapon"]:
        rows = cols = None
        item_def_id = None
    else:
        rows = cols = None
        item_def_id = None
        category = "item"
    if not name:
        flash("商品名称不能为空。", "error")
        return False
    db.session.add(ExtractionShopItem(category=category, name=name, price=price, stock=stock, shelf_until=shelf_until, storage_rows=rows, storage_cols=cols, item_def_id=item_def_id))
    db.session.commit()
    flash("商店商品已新增。", "success")
    return True


@app.route("/extraction/shop/new", methods=["GET", "POST"])
@login_required
@extraction_manage_required
def extraction_shop_new():
    sync_extraction_runtime()
    if request.method == "POST":
        ok = _create_extraction_shop_item_from_form()
        if ok:
            return redirect(url_for("extraction_shop_new"))
    return render_template("extraction_shop_new.html")


@app.route("/extraction/shop", methods=["GET", "POST"])
@login_required
@extraction_access_required
def extraction_shop():
    sync_extraction_runtime()
    if request.method == "POST" and current_user.can_manage_extraction:
        _create_extraction_shop_item_from_form()
        return redirect(url_for("extraction_shop_new"))
    today = datetime.now().date()
    shop_items = ExtractionShopItem.query.filter(ExtractionShopItem.active.is_(True)).filter((ExtractionShopItem.shelf_until.is_(None)) | (ExtractionShopItem.shelf_until >= today)).order_by(ExtractionShopItem.created_at.desc()).all()
    item_defs = ExtractionItemDef.query.filter_by(active=True).order_by(ExtractionItemDef.name.asc()).all()
    profile = get_extraction_profile(current_user.id)
    return render_template("extraction_shop.html", shop_items=shop_items, item_defs=item_defs, profile=profile)


@app.route("/extraction/shop/<int:shop_item_id>/buy", methods=["POST"])
@login_required
@extraction_access_required
def extraction_buy(shop_item_id):
    item = db.session.get(ExtractionShopItem, shop_item_id)
    profile = get_extraction_profile(current_user.id)
    if not item or not item.active or item.stock <= 0:
        flash("商品不可购买。", "error")
    elif profile.cash < item.price:
        flash("现金不足。", "error")
    elif item.shelf_until and item.shelf_until < datetime.now().date():
        flash("商品已下架。", "error")
    else:
        profile.cash -= item.price
        item.stock -= 1
        if item.category == "storage":
            profile.storage_rows = max(profile.storage_rows, item.storage_rows or profile.storage_rows)
            profile.storage_cols = max(profile.storage_cols, item.storage_cols or profile.storage_cols)
            flash("仓库已扩充。", "success")
        else:
            item_category = "武器" if item.category == "weapon" else "常规物品"
            item_def = ExtractionItemDef.query.filter_by(name=item.name, item_category=item_category).first()
            if not item_def:
                item_def = ExtractionItemDef(name=item.name, level="普通", item_category=item_category, min_price=max(1, item.price), max_price=max(1, item.price), width=1, height=1, active=True)
                db.session.add(item_def)
                db.session.flush()
            db.session.add(ExtractionInventoryItem(user_id=current_user.id, item_def_id=item_def.id, season_id=current_extraction_season().id if current_extraction_season() else None, location="buffer"))
            flash("购买成功，物品已进入缓冲区仓库。", "success")
        db.session.commit()
    return redirect(url_for("extraction_shop"))




@app.route("/extraction/shop/manage")
@login_required
@extraction_manage_required
def extraction_shop_manage():
    sync_extraction_runtime()
    items = ExtractionShopItem.query.order_by(ExtractionShopItem.active.desc(), ExtractionShopItem.created_at.desc()).all()
    return render_template("extraction_shop_manage.html", shop_items=items)


@app.route("/extraction/shop/<int:shop_item_id>/edit", methods=["GET", "POST"])
@login_required
@extraction_manage_required
def extraction_shop_edit(shop_item_id):
    item = db.session.get(ExtractionShopItem, shop_item_id)
    if not item:
        flash("商品不存在。", "error")
        return redirect(url_for("extraction_shop_manage"))
    if request.method == "POST":
        item.category = request.form.get("category", item.category)
        if item.category not in {"storage", "item", "weapon"}:
            item.category = "item"
        item.name = request.form.get("name", "").strip() or item.name
        try:
            item.price = max(0, int(request.form.get("price", item.price)))
            item.stock = max(0, int(request.form.get("stock", item.stock)))
            if item.category == "storage":
                item.storage_rows = max(1, int(request.form.get("storage_rows", item.storage_rows or 6)))
                item.storage_cols = max(1, int(request.form.get("storage_cols", item.storage_cols or 10)))
            else:
                item.storage_rows = None
                item.storage_cols = None
        except ValueError:
            flash("价格、库存和仓库尺寸必须是整数。", "error")
            return redirect(url_for("extraction_shop_edit", shop_item_id=item.id))
        shelf_until_raw = request.form.get("shelf_until", "").strip()
        item.shelf_until = datetime.strptime(shelf_until_raw, "%Y-%m-%d").date() if shelf_until_raw else None
        item.active = request.form.get("active") == "1"
        db.session.commit()
        flash("商品已更新。", "success")
        return redirect(url_for("extraction_shop_manage"))
    return render_template("extraction_shop_edit.html", item=item)


@app.route("/extraction/shop/<int:shop_item_id>/delete", methods=["POST"])
@login_required
@extraction_manage_required
def extraction_shop_delete(shop_item_id):
    item = db.session.get(ExtractionShopItem, shop_item_id)
    if item:
        db.session.delete(item)
        db.session.commit()
        flash("商品已删除。", "success")
    else:
        flash("商品不存在。", "error")
    return redirect(url_for("extraction_shop_manage"))


@app.route("/extraction/classes", methods=["GET", "POST"])
@login_required
@extraction_manage_required
def extraction_classes():
    ensure_default_extraction_rules()
    if request.method == "POST":
        for rule in ExtractionClassRule.query.order_by(ExtractionClassRule.sort_order.asc(), ExtractionClassRule.id.asc()).all():
            rule.name = request.form.get(f"name_{rule.id}", rule.name).strip() or rule.name
            try:
                rule.health = max(1, int(request.form.get(f"health_{rule.id}", rule.health)))
                rule.maintenance_fee = max(0, int(request.form.get(f"fee_{rule.id}", rule.maintenance_fee)))
            except ValueError:
                flash("血量和装备维护费用必须是整数。", "error")
                return redirect(url_for("extraction_classes"))
        db.session.commit()
        flash("兵种设置已保存。", "success")
        return redirect(url_for("extraction_classes"))
    rules = ExtractionClassRule.query.order_by(ExtractionClassRule.sort_order.asc(), ExtractionClassRule.id.asc()).all()
    return render_template("extraction_classes.html", rules=rules)


@app.route("/extraction/weapons", methods=["GET", "POST"])
@login_required
@extraction_manage_required
def extraction_weapons():
    ensure_default_extraction_rules()
    if request.method == "POST":
        for rule in ExtractionWeaponRule.query.filter_by(active=True).order_by(ExtractionWeaponRule.weapon_type.asc(), ExtractionWeaponRule.name.asc()).all():
            try:
                rule.usage_fee = max(0, int(request.form.get(f"usage_fee_{rule.id}", rule.usage_fee)))
                rule.durability_cost_percent = max(0, min(100, int(request.form.get(f"durability_{rule.id}", rule.durability_cost_percent))))
            except ValueError:
                flash("使用费和耐久消耗必须是整数。", "error")
                return redirect(url_for("extraction_weapons"))
        db.session.commit()
        flash("武器设置已保存。", "success")
        return redirect(url_for("extraction_weapons"))
    rules = ExtractionWeaponRule.query.filter_by(active=True).order_by(ExtractionWeaponRule.weapon_type.asc(), ExtractionWeaponRule.name.asc()).all()
    return render_template("extraction_weapons.html", rules=rules)


@app.route("/extraction/matches", methods=["GET", "POST"])
@login_required
@extraction_access_required
def extraction_matches():
    sync_extraction_runtime()
    if request.method == "POST":
        if not current_user.can_manage_extraction:
            flash("需要管理员权限。", "error")
            return redirect(url_for("extraction_matches"))
        name = request.form.get("name", "").strip()
        venue_name = request.form.get("venue_name", "").strip()
        try:
            squad_count = max(1, min(20, int(request.form.get("squad_count", "2"))))
            squad_limit = max(1, min(50, int(request.form.get("squad_limit", "5"))))
        except ValueError:
            flash("小队数和人数上限必须是整数。", "error")
            return redirect(url_for("extraction_matches"))
        if not name:
            flash("请输入对局名称。", "error")
        else:
            db.session.add(ExtractionMatch(name=name, venue_name=venue_name, squad_count=squad_count, squad_limit=squad_limit, created_by_id=current_user.id))
            db.session.commit()
            flash("对局已创建。", "success")
        return redirect(url_for("extraction_matches"))
    matches = active_extraction_matches_query().all()
    venues = Venue.query.order_by(Venue.name.asc()).all()
    return render_template("extraction_matches.html", matches=matches, venues=venues, status_label=extraction_match_status_label)


@app.route("/extraction/match-records")
@login_required
@extraction_access_required
def extraction_match_records():
    sync_extraction_runtime()
    only_mine = request.args.get("mine") == "1"
    query = ExtractionMatch.query.filter(ExtractionMatch.status == "ended")
    if not current_user.can_manage_extraction or only_mine:
        query = query.join(ExtractionMatchParticipant).filter(ExtractionMatchParticipant.user_id == current_user.id)
    matches = query.order_by(ExtractionMatch.ended_at.desc().nullslast(), ExtractionMatch.created_at.desc()).all()
    return render_template("extraction_match_records.html", matches=matches, only_mine=only_mine, status_label=extraction_match_status_label)




@app.route("/extraction/matches/<int:match_id>/enter")
@login_required
@extraction_access_required
def extraction_match_enter(match_id):
    match = db.session.get(ExtractionMatch, match_id)
    if not match or match.status == "cancelled":
        flash("对局不存在。", "error")
        return redirect(url_for("extraction_matches"))
    if match.status == "preparing" and not ExtractionMatchParticipant.query.filter_by(match_id=match.id, user_id=current_user.id).first():
        db.session.add(ExtractionMatchParticipant(match_id=match.id, user_id=current_user.id))
        db.session.commit()
        flash("已自动加入对局，请选择小队、兵种和武器。", "success")
    return redirect(url_for("extraction_match_detail", match_id=match.id))

@app.route("/extraction/matches/<int:match_id>")
@login_required
@extraction_access_required
def extraction_match_detail(match_id):
    sync_extraction_runtime()
    match = db.session.get(ExtractionMatch, match_id)
    if not match or match.status == "cancelled":
        flash("对局不存在。", "error")
        return redirect(url_for("extraction_matches"))
    participant = ExtractionMatchParticipant.query.filter_by(match_id=match.id, user_id=current_user.id).first()
    participants = ExtractionMatchParticipant.query.filter_by(match_id=match.id).order_by(ExtractionMatchParticipant.squad_no.asc(), ExtractionMatchParticipant.created_at.asc()).all()
    class_rules = ExtractionClassRule.query.filter_by(active=True).order_by(ExtractionClassRule.sort_order.asc(), ExtractionClassRule.id.asc()).all()
    weapon_choices = get_user_weapon_choices(current_user.id)
    item_defs = ExtractionItemDef.query.filter_by(active=True).order_by(ExtractionItemDef.item_category.desc(), ExtractionItemDef.name.asc()).all()
    venues = Venue.query.order_by(Venue.name.asc()).all()
    return render_template("extraction_match_detail.html", match=match, participant=participant, participants=participants, class_rules=class_rules, weapon_choices=weapon_choices, item_defs=item_defs, venues=venues, season=current_extraction_season(), status_label=extraction_match_status_label, participant_cost=participant_cost)


@app.route("/extraction/matches/<int:match_id>/edit", methods=["POST"])
@login_required
@extraction_manage_required
def extraction_match_edit(match_id):
    match = db.session.get(ExtractionMatch, match_id)
    if not match or match.status != "preparing":
        flash("只有开局整备中的对局可以编辑。", "error")
        return redirect(url_for("extraction_matches"))
    match.name = request.form.get("name", "").strip() or match.name
    match.venue_name = request.form.get("venue_name", "").strip()
    try:
        match.squad_count = max(1, min(20, int(request.form.get("squad_count", match.squad_count))))
        match.squad_limit = max(1, min(50, int(request.form.get("squad_limit", match.squad_limit))))
    except ValueError:
        flash("小队数和人数上限必须是整数。", "error")
    db.session.commit()
    flash("对局已更新。", "success")
    return redirect(url_for("extraction_match_detail", match_id=match.id))


@app.route("/extraction/matches/<int:match_id>/join", methods=["POST"])
@login_required
@extraction_access_required
def extraction_match_join(match_id):
    match = db.session.get(ExtractionMatch, match_id)
    if not match or match.status != "preparing":
        flash("当前对局不能加入。", "error")
        return redirect(url_for("extraction_matches"))
    participant = ExtractionMatchParticipant.query.filter_by(match_id=match.id, user_id=current_user.id).first()
    if not participant:
        db.session.add(ExtractionMatchParticipant(match_id=match.id, user_id=current_user.id))
        db.session.commit()
        flash("已加入对局，请选择小队、兵种和武器。", "success")
    return redirect(url_for("extraction_match_detail", match_id=match.id))


@app.route("/extraction/matches/<int:match_id>/configure", methods=["POST"])
@login_required
@extraction_access_required
def extraction_match_configure(match_id):
    match = db.session.get(ExtractionMatch, match_id)
    participant = ExtractionMatchParticipant.query.filter_by(match_id=match_id, user_id=current_user.id).first()
    if not match or not participant or match.status != "preparing":
        flash("当前对局不能修改。", "error")
        return redirect(url_for("extraction_matches"))
    if participant.locked:
        flash("你已经锁定，不能修改。", "error")
        return redirect(url_for("extraction_match_detail", match_id=match.id))
    try:
        squad_no = int(request.form.get("squad_no", "0"))
        class_rule_id = int(request.form.get("class_rule_id", "0"))
    except ValueError:
        flash("请选择小队和兵种。", "error")
        return redirect(url_for("extraction_match_detail", match_id=match.id))
    if squad_no < 1 or squad_no > match.squad_count:
        flash("小队无效。", "error")
        return redirect(url_for("extraction_match_detail", match_id=match.id))
    squad_used = ExtractionMatchParticipant.query.filter(ExtractionMatchParticipant.match_id == match.id, ExtractionMatchParticipant.squad_no == squad_no, ExtractionMatchParticipant.id != participant.id).count()
    if squad_used >= match.squad_limit:
        flash("该小队人数已满。", "error")
        return redirect(url_for("extraction_match_detail", match_id=match.id))
    class_rule = db.session.get(ExtractionClassRule, class_rule_id)
    weapon_rule, inv = parse_weapon_choice(current_user.id, request.form.get("weapon_choice", ""))
    if not class_rule or not class_rule.active or not weapon_rule:
        flash("请选择有效兵种和武器。", "error")
        return redirect(url_for("extraction_match_detail", match_id=match.id))
    if class_rule.name == "跑刀仔" and weapon_rule.weapon_type != "knife":
        flash("跑刀仔只能选择刀。", "error")
        return redirect(url_for("extraction_match_detail", match_id=match.id))
    participant.squad_no = squad_no
    participant.class_rule_id = class_rule.id
    participant.weapon_rule_id = weapon_rule.id
    participant.weapon_inventory_item_id = inv.id if inv else None
    db.session.commit()
    flash("整备信息已保存。", "success")
    return redirect(url_for("extraction_match_detail", match_id=match.id))


@app.route("/extraction/matches/<int:match_id>/lock", methods=["POST"])
@login_required
@extraction_access_required
def extraction_match_lock(match_id):
    match = db.session.get(ExtractionMatch, match_id)
    participant = ExtractionMatchParticipant.query.filter_by(match_id=match_id, user_id=current_user.id).first()
    if not match or not participant or match.status != "preparing":
        flash("当前对局不能锁定。", "error")
    elif not participant.squad_no or not participant.class_rule_id or not participant.weapon_rule_id:
        flash("请先选择小队、兵种和武器。", "error")
    else:
        participant.locked = True
        db.session.commit()
        flash("已锁定。", "success")
    return redirect(url_for("extraction_match_detail", match_id=match_id))


@app.route("/extraction/matches/<int:match_id>/unlock/<int:participant_id>", methods=["POST"])
@login_required
@extraction_manage_required
def extraction_match_unlock(match_id, participant_id):
    participant = db.session.get(ExtractionMatchParticipant, participant_id)
    if participant and participant.match_id == match_id and participant.match.status == "preparing":
        participant.locked = False
        db.session.commit()
        flash("已解除锁定。", "success")
    return redirect(url_for("extraction_match_detail", match_id=match_id))


@app.route("/extraction/matches/<int:match_id>/start", methods=["POST"])
@login_required
@extraction_manage_required
def extraction_match_start(match_id):
    match = db.session.get(ExtractionMatch, match_id)
    if not match or match.status != "preparing":
        flash("当前对局不能开局。", "error")
        return redirect(url_for("extraction_matches"))
    participants = ExtractionMatchParticipant.query.filter_by(match_id=match.id).all()
    if not participants:
        flash("无人加入，不能开局。", "error")
        return redirect(url_for("extraction_match_detail", match_id=match.id))
    if any(not p.locked for p in participants):
        flash("还有用户未锁定，不能开局。", "error")
        return redirect(url_for("extraction_match_detail", match_id=match.id))
    for p in participants:
        cost = participant_cost(p)
        profile = get_extraction_profile(p.user_id)
        if p.weapon_rule and p.weapon_rule.weapon_type == "special":
            inv = p.weapon_inventory_item
            if not inv or inv.sold_at or inv.location != "storage" or inv.user_id != p.user_id:
                flash(f"{p.user.callsign} 的特殊武器已不可用，不能开局。", "error")
                return redirect(url_for("extraction_match_detail", match_id=match.id))
        if profile.cash < cost:
            flash(f"{p.user.callsign} 现金不足，不能开局。", "error")
            return redirect(url_for("extraction_match_detail", match_id=match.id))
    for p in participants:
        cost = participant_cost(p)
        profile = get_extraction_profile(p.user_id)
        profile.cash -= cost
        p.paid_cash = cost
        if p.weapon_rule and p.weapon_rule.weapon_type == "special" and p.weapon_inventory_item:
            p.weapon_inventory_item.location = "in_match"
            p.weapon_inventory_item.row = None
            p.weapon_inventory_item.col = None
    match.status = "started"
    match.started_at = datetime.now()
    db.session.commit()
    flash("对局已开始，已扣除装备维护费用和武器使用费。", "success")
    return redirect(url_for("extraction_match_detail", match_id=match.id))


@app.route("/extraction/matches/<int:match_id>/settle", methods=["POST"])
@login_required
@extraction_manage_required
def extraction_match_settle(match_id):
    match = db.session.get(ExtractionMatch, match_id)
    if not match or match.status != "started":
        flash("只有进行中的对局可以终局结算。", "error")
        return redirect(url_for("extraction_matches"))
    participants = ExtractionMatchParticipant.query.filter_by(match_id=match.id).all()
    season = current_extraction_season()
    kill_reward_unit = int(season.kill_reward_cash or 0) if season else 0
    for p in participants:
        p.evacuated = request.form.get(f"evacuated_{p.id}") == "1"
        try:
            p.kills = max(0, int(request.form.get(f"kills_{p.id}", "0")))
            p.earned_cash = max(0, int(request.form.get(f"cash_{p.id}", "0")))
        except ValueError:
            p.kills = 0
            p.earned_cash = 0
        p.kill_reward_cash = int(p.kills or 0) * kill_reward_unit
        profile = get_extraction_profile(p.user_id)
        profile.cash += int(p.earned_cash or 0) + int(p.kill_reward_cash or 0)
        reward_item_ids = request.form.getlist(f"reward_item_{p.id}[]")
        reward_qtys = request.form.getlist(f"reward_qty_{p.id}[]")
        # Backward compatibility with the older single-item settlement form.
        if not reward_item_ids:
            single_item = request.form.get(f"reward_item_{p.id}")
            single_qty = request.form.get(f"reward_qty_{p.id}")
            if single_item:
                reward_item_ids = [single_item]
                reward_qtys = [single_qty or "0"]
        for idx, raw_item_id in enumerate(reward_item_ids):
            try:
                reward_item_id = int(raw_item_id or 0)
            except (TypeError, ValueError):
                reward_item_id = 0
            try:
                reward_qty = int(reward_qtys[idx]) if idx < len(reward_qtys) else 0
            except (TypeError, ValueError):
                reward_qty = 0
            reward_qty = max(0, min(reward_qty, 99))
            if reward_item_id and reward_qty > 0:
                item_def = db.session.get(ExtractionItemDef, reward_item_id)
                if item_def:
                    for _ in range(reward_qty):
                        db.session.add(ExtractionInventoryItem(user_id=p.user_id, item_def_id=item_def.id, season_id=season.id if season else None, location="buffer", match_participant_id=p.id))
        if p.weapon_rule and p.weapon_rule.weapon_type == "special" and p.weapon_inventory_item:
            inv = p.weapon_inventory_item
            if p.evacuated:
                inv.durability_percent = int(inv.durability_percent or 100) - int(p.weapon_rule.durability_cost_percent or 0)
                if inv.durability_percent <= 0:
                    db.session.delete(inv)
                else:
                    inv.location = "storage"
                    row, col = find_storage_slot_for_item(p.user_id, inv.item_def)
                    inv.row = row
                    inv.col = col
            else:
                db.session.delete(inv)
        p.settled_at = datetime.now()
    match.status = "ended"
    match.ended_at = datetime.now()
    db.session.commit()
    flash("对局已终局结算。", "success")
    return redirect(url_for("extraction_match_detail", match_id=match.id))

@app.route("/extraction/seasons", methods=["GET", "POST"])
@login_required
@extraction_manage_required
def extraction_seasons():
    if request.method == "POST" and request.form.get("action") == "banner":
        banner = request.files.get("banner")
        if not banner or not banner.filename:
            flash("请选择主页图片。", "error")
            return redirect(url_for("extraction_seasons"))
        try:
            banner_filename = save_uploaded_photo(banner, "extraction_banner", "yongcheng_banner")
        except ValueError as exc:
            flash(str(exc), "error")
            return redirect(url_for("extraction_seasons"))
        setting = get_extraction_ui_setting()
        setting.banner_filename = banner_filename
        db.session.commit()
        flash("逃离西撇镇入口图片已更新。", "success")
        return redirect(url_for("extraction_seasons"))
    if request.method == "POST":
        name = request.form.get("name", "").strip()
        try:
            start_date = datetime.strptime(request.form.get("start_date"), "%Y-%m-%d").date()
            end_date = datetime.strptime(request.form.get("end_date"), "%Y-%m-%d").date()
        except Exception:
            flash("请选择有效的开始和结束日期。", "error")
            return redirect(url_for("extraction_seasons"))
        try:
            kill_reward_cash = max(0, int(request.form.get("kill_reward_cash", "0") or 0))
        except ValueError:
            flash("每个人头现金奖励必须是整数。", "error")
            return redirect(url_for("extraction_seasons"))
        if not name:
            flash("赛季名称不能为空。", "error")
        elif end_date < start_date:
            flash("结束日期不能早于开始日期。", "error")
        else:
            db.session.add(ExtractionSeason(name=name, start_date=start_date, end_date=end_date, kill_reward_cash=kill_reward_cash, created_by_id=current_user.id))
            db.session.commit()
            flash("赛季已创建。", "success")
        return redirect(url_for("extraction_seasons"))
    deactivate_expired_extraction_seasons()
    seasons = ExtractionSeason.query.order_by(ExtractionSeason.start_date.desc()).all()
    return render_template("extraction_seasons.html", seasons=seasons, ui_setting=get_extraction_ui_setting())


@app.route("/extraction/seasons/<int:season_id>/toggle", methods=["POST"])
@login_required
@extraction_manage_required
def extraction_toggle_season(season_id):
    season = db.session.get(ExtractionSeason, season_id)
    if season:
        season.active = not season.active
        db.session.commit()
        flash("赛季状态已更新。", "success")
    return redirect(url_for("extraction_seasons"))


@app.route("/extraction/seasons/<int:season_id>/reward", methods=["POST"])
@login_required
@extraction_manage_required
def extraction_update_season_reward(season_id):
    season = db.session.get(ExtractionSeason, season_id)
    if not season:
        flash("赛季不存在。", "error")
        return redirect(url_for("extraction_seasons"))
    try:
        season.kill_reward_cash = max(0, int(request.form.get("kill_reward_cash", season.kill_reward_cash or 0)))
    except ValueError:
        flash("每个人头现金奖励必须是整数。", "error")
        return redirect(url_for("extraction_seasons"))
    db.session.commit()
    flash("击杀奖励已更新。", "success")
    return redirect(url_for("extraction_seasons"))


# =========================
# V57 API routes for future Vue3 frontends
# These APIs are additive and keep the old Flask template pages working.
# =========================

def api_user_payload(user: User):
    return {
        "id": user.id,
        "username": user.username,
        "callsign": user.callsign,
        "role": user.role,
        "is_admin": bool(user.is_admin),
        "is_superadmin": bool(user.is_superadmin),
        "is_regular_member": bool(user.is_regular_member),
        "attendance_manager": bool(user.attendance_manager),
        "extraction_authorized": bool(user.extraction_authorized),
        "extraction_manager": bool(user.extraction_manager),
        "can_manage_attendance": bool(user.can_manage_attendance),
        "can_access_extraction": bool(user.can_access_extraction),
        "can_manage_extraction": bool(user.can_manage_extraction),
    }


def api_activity_payload(activity: Activity):
    count = enrollment_count(activity.id)
    status_text, status_class = activity_status(activity)
    return {
        "id": activity.id,
        "name": activity.name,
        "start_at": activity.start_at.isoformat(sep=" ", timespec="minutes"),
        "end_at": activity.end_at.isoformat(sep=" ", timespec="minutes"),
        "location": activity.location,
        "open_min": activity.open_min,
        "camp_count": activity.camp_count,
        "squad_count": activity.squad_count,
        "enrollment_count": count,
        "status_text": status_text,
        "status_class": status_class,
        "activity_region": getattr(activity, "activity_region", "宁波"),
        "visibility_type": getattr(activity, "visibility_type", "all"),
        "visibility_label": activity_visibility_label(activity),
        "can_view_visibility_info": can_view_activity_visibility_info(activity),
        "game_modes": activity.game_mode_list,
        "allowed_jobs": activity.allowed_job_list,
    }


@app.route("/api/health")
def api_health():
    return jsonify({"ok": True, "app": APP_TITLE, "version": "v57-api-scaffold"})


@app.route("/api/auth/me")
def api_auth_me():
    if not current_user.is_authenticated:
        return jsonify({"authenticated": False}), 401
    return jsonify({"authenticated": True, "user": api_user_payload(current_user)})


@app.route("/api/auth/login", methods=["POST"])
def api_auth_login():
    payload = request.get_json(silent=True) or request.form
    username = (payload.get("username") or "").strip()
    password = payload.get("password") or ""
    user = User.query.filter_by(username=username).first()
    if not user or not user.check_password(password):
        return jsonify({"ok": False, "message": "用户名或密码错误。"}), 400
    if user.disabled:
        return jsonify({"ok": False, "message": "该用户已被禁用。"}), 403
    login_user(user)
    user.last_seen = datetime.now()
    db.session.commit()
    return jsonify({"ok": True, "user": api_user_payload(user)})


@app.route("/api/auth/logout", methods=["POST"])
@login_required
def api_auth_logout():
    logout_user()
    return jsonify({"ok": True})


@app.route("/api/dashboard")
@login_required
def api_dashboard():
    try:
        sync_attendance_events()
    except Exception:
        db.session.rollback()
    cards = []
    activities = Activity.query.filter(Activity.deleted_at.is_(None)).order_by(Activity.start_at.desc()).all()
    for activity in activities:
        status_text, _ = activity_status(activity)
        if status_text == "活动结束":
            continue
        if not current_user.is_admin:
            if status_text not in {"报名中", "已锁定", "活动进行中"}:
                continue
            if not can_view_activity(activity):
                continue
        cards.append(api_activity_payload(activity))
    return jsonify({
        "user": api_user_payload(current_user),
        "stats": {
            "users_count": User.query.count(),
            "online_count": User.query.filter(User.last_seen >= datetime.now() - timedelta(minutes=5)).count(),
        },
        "activities": cards,
        "extraction_enabled": bool(current_user.can_access_extraction),
    })


@app.route("/api/activities/<int:activity_id>")
@login_required
def api_activity_detail(activity_id):
    activity = db.session.get(Activity, activity_id)
    if not activity or not can_view_activity(activity):
        return jsonify({"ok": False, "message": "无权查看该活动。"}), 404
    enrollments = Enrollment.query.filter_by(activity_id=activity.id).order_by(Enrollment.created_at.asc()).all()
    my_enrollment = next((en for en in enrollments if en.user_id == current_user.id), None)
    return jsonify({
        "activity": api_activity_payload(activity),
        "my_enrollment": None if not my_enrollment else {
            "camp_no": my_enrollment.camp_no,
            "squad_no": my_enrollment.squad_no,
            "job": my_enrollment.job,
        },
        "enrollments": [
            {
                "id": en.id,
                "user_id": en.user_id,
                "callsign": en.user.callsign if en.user else "",
                "camp_no": en.camp_no,
                "squad_no": en.squad_no,
                "job": en.job,
            }
            for en in enrollments
            if current_user.is_admin or en.user_id == current_user.id or current_user.is_regular_member
        ],
    })


@app.route("/api/extraction/profile")
@login_required
def api_extraction_profile():
    if not current_user.can_access_extraction:
        return jsonify({"ok": False, "message": "未授权访问逃离西撇镇。"}), 403
    deactivate_expired_extraction_seasons()
    refresh_extraction_prices()
    auto_sell_extraction_buffers()
    profile = get_extraction_profile(current_user.id)
    items = ExtractionInventoryItem.query.filter_by(user_id=current_user.id, sold_at=None).all()
    return jsonify({
        "user": api_user_payload(current_user),
        "profile": {
            "cash": profile.cash,
            "runs_count": profile.runs_count,
            "warehouse_rows": profile.storage_rows,
            "warehouse_cols": profile.storage_cols,
        },
        "items": [
            {
                "id": item.id,
                "name": item.item_def.name,
                "level": item.item_def.level,
                "category": getattr(item.item_def, "item_category", "常规物品"),
                "location": item.location,
                "row": item.row,
                "col": item.col,
                "width": item.item_def.width,
                "height": item.item_def.height,
                "image_path": item.item_def.photo_filename,
                "price": extraction_today_price(item.item_def),
                "trend": extraction_trend(item.item_def)[1],
            }
            for item in items
        ],
    })


if __name__ == "__main__":
    init_db()
    app.run(host="0.0.0.0", port=574, debug=True)
else:
    init_db()
