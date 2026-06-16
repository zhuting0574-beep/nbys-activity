import io
import os
import tempfile
import unittest

os.environ.setdefault("DATA_DIR", tempfile.mkdtemp(prefix="nbys-photo-test-data-"))

from werkzeug.datastructures import FileStorage

from app import uploaded_photo_extension


class UploadedPhotoExtensionTest(unittest.TestCase):
    def upload(self, filename, content):
        return FileStorage(stream=io.BytesIO(content), filename=filename)

    def test_accepts_chinese_jpg_filename(self):
        photo = self.upload("物品照片.jpg", b"\xff\xd8\xff\xe0test")
        self.assertEqual(uploaded_photo_extension(photo), "jpg")
        self.assertEqual(photo.stream.tell(), 0)

    def test_accepts_uppercase_jpeg(self):
        photo = self.upload("微信图片_20260615.JPEG", b"\xff\xd8\xff\xe1test")
        self.assertEqual(uploaded_photo_extension(photo), "jpg")

    def test_accepts_png_and_webp(self):
        png = self.upload("物品.PNG", b"\x89PNG\r\n\x1a\nrest")
        webp = self.upload("物品.webp", b"RIFF\x10\x00\x00\x00WEBPrest")
        self.assertEqual(uploaded_photo_extension(png), "png")
        self.assertEqual(uploaded_photo_extension(webp), "webp")

    def test_rejects_fake_jpg(self):
        photo = self.upload("伪装图片.jpg", b"this is not a jpeg")
        with self.assertRaisesRegex(ValueError, "内容与文件扩展名不一致"):
            uploaded_photo_extension(photo)


if __name__ == "__main__":
    unittest.main()
