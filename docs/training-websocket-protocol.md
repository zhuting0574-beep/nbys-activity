# Training WebSocket Protocol v1

Endpoint: `ws(s)://host/ws/training?token=<access-token>&device_code=<node>`.

Client messages: `hello`, `heartbeat`, `bind_target`, `hit`, `sync_pending`.
Server messages: `room_snapshot`, `session_started`, `session_completed`, `target_bound`, `error`.

`hit` fields: `event_id`, `session_id`, `target_no`, `shot_no`, `hit_at_ms`, `split_ms`, `ring_score`, `accuracy`, `x_ratio`, `y_ratio`. `event_id` must be globally unique; duplicate events are ignored.

The REST `POST /api/training/h5/sessions/{id}/hits` endpoint uses the identical payload as a durable offline fallback.
