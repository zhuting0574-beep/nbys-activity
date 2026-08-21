ALTER TABLE activities
  ADD COLUMN external_miniapp_qr_url varchar(500) DEFAULT NULL COMMENT '外部第三方小程序活动二维码' AFTER banner_url;
