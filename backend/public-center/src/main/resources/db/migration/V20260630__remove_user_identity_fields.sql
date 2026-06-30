-- 手机号和身份证信息不再采集或保存，删除历史字段及其中已有的数据。
ALTER TABLE users
  DROP COLUMN phone,
  DROP COLUMN id_card;
