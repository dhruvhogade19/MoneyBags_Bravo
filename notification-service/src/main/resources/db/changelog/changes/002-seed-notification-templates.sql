--liquibase formatted sql

--changeset charuvi:005-seed-notification-templates
INSERT INTO notification_template (notification_type, email_subject_template, email_body_template, active, created_at, updated_at) VALUES
('KYC_APPROVED', 'Your KYC verification is approved', 'Hello {{firstName}}, your KYC verification has been approved.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO notification_template (notification_type, email_subject_template, email_body_template, active, created_at, updated_at) VALUES
('KYC_REJECTED', 'Update on your KYC verification', 'Hello {{firstName}}, your KYC verification could not be approved. {{rejectionReason}}', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO notification_template (notification_type, email_subject_template, email_body_template, active, created_at, updated_at) VALUES
('DEPOSIT_ACCOUNT_CREATED', 'Your deposit account is ready', 'Hello {{firstName}}, your {{accountType}} account {{accountId}} has been created.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO notification_template (notification_type, email_subject_template, email_body_template, active, created_at, updated_at) VALUES
('FD_MATURITY', 'Your fixed deposit has matured', 'Hello {{firstName}}, fixed deposit {{accountId}} has matured on {{maturityDate}}. Maturity amount: {{currency}} {{maturityAmount}}.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO notification_template (notification_type, email_subject_template, email_body_template, active, created_at, updated_at) VALUES
('CREDIT_CARD_CREATED', 'Your credit card account is ready', 'Hello {{firstName}}, your credit card account {{accountId}} has been created. Card ending: {{cardLastFour}}.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO notification_template (notification_type, email_subject_template, email_body_template, active, created_at, updated_at) VALUES
('PAYMENT_SUCCESS', 'Your payment was successful', 'Hello {{firstName}}, your {{paymentType}} payment of {{currency}} {{amount}} was successful on {{transactionDate}}. {{reference}}', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO notification_template (notification_type, email_subject_template, email_body_template, active, created_at, updated_at) VALUES
('PAYMENT_FAILED', 'Your payment could not be completed', 'Hello {{firstName}}, your {{paymentType}} payment of {{currency}} {{amount}} could not be completed on {{transactionDate}}. {{failureReason}}', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO notification_template (notification_type, email_subject_template, email_body_template, active, created_at, updated_at) VALUES
('PAYMENT_REVERSED', 'Your payment was reversed', 'Hello {{firstName}}, your {{paymentType}} payment of {{currency}} {{amount}} was reversed on {{transactionDate}}. {{reversalReason}}', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO notification_template (notification_type, email_subject_template, email_body_template, active, created_at, updated_at) VALUES
('BILL_GENERATED', 'Your credit card bill is ready', 'Hello {{firstName}}, bill {{billId}} for {{billingPeriod}} is ready. Total due: {{currency}} {{totalAmount}}. Due date: {{dueDate}}.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO notification_template (notification_type, email_subject_template, email_body_template, active, created_at, updated_at) VALUES
('PAYMENT_DUE_REMINDER', 'Credit card payment reminder', 'Hello {{firstName}}, payment of {{currency}} {{amountDue}} for bill {{billId}} is due on {{dueDate}}.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO notification_template (notification_type, email_subject_template, email_body_template, active, created_at, updated_at) VALUES
('STATEMENT_READY', 'Your statement is ready', 'Hello {{firstName}}, your statement {{statementId}} for {{statementPeriod}} is ready.', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
