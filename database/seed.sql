-- =============================================================
-- Distributed Bulk Email Sender - Seed Data
-- =============================================================
-- Run after schema.sql to insert sample data for testing.
-- Execute: psql -U emailuser -d emaildb -f seed.sql
-- =============================================================

-- =============================================================
-- Sample Email Templates with dynamic placeholders
-- Assignment requirement: "Email templates with dynamic placeholders"
-- Supported placeholders: {{name}}, {{studentNumber}}, {{course}}
-- =============================================================

INSERT INTO email_templates (name, subject_template, body_template, description) VALUES
(
    'Welcome Student Email',
    'Welcome to {{course}}, {{name}}!',
    '<html><body>
<h2>Welcome to {{course}}!</h2>
<p>Dear {{name}},</p>
<p>We are excited to welcome you to the <strong>{{course}}</strong> program.</p>
<p>Your student number is: <strong>{{studentNumber}}</strong></p>
<p>Please log in to the student portal to access your course materials.</p>
<br>
<p>Best regards,<br>The Academic Team</p>
</body></html>',
    'Sent to new students upon enrollment'
),
(
    'Assignment Due Reminder',
    'Reminder: {{course}} Assignment Due Soon!',
    '<html><body>
<h2>Assignment Deadline Reminder</h2>
<p>Dear {{name}},</p>
<p>This is a reminder that your assignment for <strong>{{course}}</strong> is due in 48 hours.</p>
<p>Student Number: <strong>{{studentNumber}}</strong></p>
<p>Please submit your work through the course portal before the deadline.</p>
<p>If you need an extension, contact your lecturer immediately.</p>
<br>
<p>Good luck!<br>CS Department</p>
</body></html>',
    'Sent 48 hours before assignment deadline'
),
(
    'Grade Published Notification',
    'Your {{course}} Grade is Available',
    '<html><body>
<h2>Grade Published</h2>
<p>Dear {{name}},</p>
<p>Your grade for <strong>{{course}}</strong> has been published.</p>
<p>Student Number: <strong>{{studentNumber}}</strong></p>
<p>Log in to the student portal to view your results.</p>
<br>
<p>Regards,<br>Academic Records</p>
</body></html>',
    'Sent when grades are published'
),
(
    'System Maintenance Alert',
    'Scheduled Maintenance: {{system}} on {{date}}',
    '<html><body>
<h2>Scheduled System Maintenance</h2>
<p>Dear {{name}},</p>
<p>Please be informed that <strong>{{system}}</strong> will undergo scheduled maintenance on <strong>{{date}}</strong>.</p>
<p>Expected downtime: {{duration}}</p>
<p>We apologize for any inconvenience.</p>
<br>
<p>IT Operations Team</p>
</body></html>',
    'System-wide maintenance notifications'
);

-- =============================================================
-- Sample Campaigns
-- =============================================================

INSERT INTO email_campaigns (name, subject, template_id, sender_email, sender_name, total_recipients, sent_count, failed_count)
VALUES
(
    'CS401 Welcome Batch - April 2026',
    'Welcome to CS401 Distributed Systems!',
    1,
    'admin@university.edu',
    'CS Department',
    5,
    4,
    1
),
(
    'Assignment 2 Due Reminder',
    'Reminder: CS401 Assignment 2 Due Tomorrow!',
    2,
    'reminders@university.edu',
    'Academic Team',
    3,
    3,
    0
);

-- =============================================================
-- Sample Email Tasks
-- Assignment requirements: priority, status, provider tracking, retry
-- =============================================================

-- Campaign 1 tasks
INSERT INTO email_tasks (campaign_id, recipient_email, recipient_name, template_variables, priority, status, provider_used, retry_count)
VALUES
(1, 'alice@student.edu', 'Alice Smith',
 '{"studentNumber":"ST001","course":"CS401 Distributed Systems"}',
 'HIGH', 'SENT', 'MAILGUN', 0),

(1, 'bob@student.edu', 'Bob Jones',
 '{"studentNumber":"ST002","course":"CS401 Distributed Systems"}',
 'HIGH', 'SENT', 'MAILGUN', 0),

(1, 'carol@student.edu', 'Carol Williams',
 '{"studentNumber":"ST003","course":"CS401 Distributed Systems"}',
 'HIGH', 'FAILED', 'SENDGRID', 2),

(1, 'david@student.edu', 'David Brown',
 '{"studentNumber":"ST004","course":"CS401 Distributed Systems"}',
 'HIGH', 'DELIVERED', 'MAILGUN', 0),

(1, 'eve@student.edu', 'Eve Davis',
 '{"studentNumber":"ST005","course":"CS401 Distributed Systems"}',
 'HIGH', 'DEAD_LETTERED', 'SENDGRID', 3);

-- Campaign 2 tasks
INSERT INTO email_tasks (campaign_id, recipient_email, recipient_name, template_variables, priority, status, provider_used, retry_count)
VALUES
(2, 'alice@student.edu', 'Alice Smith',
 '{"studentNumber":"ST001","course":"CS401 Distributed Systems"}',
 'NORMAL', 'SENT', 'MAILGUN', 0),

(2, 'bob@student.edu', 'Bob Jones',
 '{"studentNumber":"ST002","course":"CS401 Distributed Systems"}',
 'NORMAL', 'SENT', 'SENDGRID', 1),

(2, 'frank@student.edu', 'Frank Lee',
 '{"studentNumber":"ST006","course":"CS401 Distributed Systems"}',
 'NORMAL', 'SENT', 'MAILGUN', 0);

-- =============================================================
-- Sample Status Logs
-- Assignment requirement: "Every send attempt must be logged in PostgreSQL"
-- =============================================================

-- Alice - task 1 (successful)
INSERT INTO email_status_logs (email_task_id, status, provider, message, response_code)
VALUES
(1, 'QUEUED', 'NONE', 'Task published to Kafka high-priority topic', NULL),
(1, 'SENDING', 'MAILGUN', 'Attempting delivery via Mailgun', NULL),
(1, 'SENT', 'MAILGUN', 'Accepted by Mailgun: <abc123@mailgun.org>', 200);

-- Carol - task 3 (failed with retries, then sent via SendGrid)
INSERT INTO email_status_logs (email_task_id, status, provider, message, response_code)
VALUES
(3, 'QUEUED', 'NONE', 'Task published to Kafka high-priority topic', NULL),
(3, 'SENDING', 'MAILGUN', 'Attempting delivery via Mailgun', NULL),
(3, 'FAILED', 'MAILGUN', 'Mailgun HTTP 500: Internal Server Error', 500),
(3, 'RETRYING', 'MAILGUN', 'Scheduled retry #1 with exponential backoff (2000ms)', NULL),
(3, 'SENDING', 'SENDGRID', 'Failover: attempting delivery via SendGrid', NULL),
(3, 'FAILED', 'SENDGRID', 'SendGrid HTTP 503: Service Unavailable', 503),
(3, 'RETRYING', 'SENDGRID', 'Scheduled retry #2 with exponential backoff (4000ms)', NULL),
(3, 'FAILED', 'SENDGRID', 'SendGrid network error: Connection timeout', 0);

-- Eve - task 5 (dead-lettered after 3 retries)
INSERT INTO email_status_logs (email_task_id, status, provider, message, response_code)
VALUES
(5, 'QUEUED', 'NONE', 'Task published to Kafka high-priority topic', NULL),
(5, 'SENDING', 'MAILGUN', 'Attempting delivery via Mailgun', NULL),
(5, 'FAILED', 'MAILGUN', 'Mailgun HTTP 500: Domain not configured', 500),
(5, 'SENDING', 'SENDGRID', 'Failover to SendGrid', NULL),
(5, 'FAILED', 'SENDGRID', 'SendGrid HTTP 400: Invalid recipient email', 400),
(5, 'DEAD_LETTERED', 'SENDGRID', 'Max retries exceeded (3). Moved to dead-letter topic.', NULL);

-- All done! Sample data inserted.
-- You can now start the services and see real data in the JavaFX GUI.
