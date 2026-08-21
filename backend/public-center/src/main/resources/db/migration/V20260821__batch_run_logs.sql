CREATE TABLE IF NOT EXISTS batch_run_logs (
  id bigint NOT NULL AUTO_INCREMENT,
  task_key varchar(60) NOT NULL,
  task_name varchar(120) NOT NULL,
  business_date date NOT NULL,
  trigger_type varchar(20) NOT NULL COMMENT 'scheduled/manual',
  status varchar(20) NOT NULL COMMENT 'running/success/failed',
  started_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at datetime DEFAULT NULL,
  message varchar(1000) DEFAULT NULL,
  result_json longtext,
  created_by int DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_batch_run_task_date (task_key,business_date,id),
  KEY idx_batch_run_status (status,started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
