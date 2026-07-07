alter table activities
  add column checkin_methods varchar(30) not null default 'location,qr' comment '签到方式：location,qr' after venue_id;
