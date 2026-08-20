-- ============================================================
-- RAILWAY RESERVATION SYSTEM - DBMS MINI PROJECT
-- Database: MySQL 8.0+
-- ============================================================

-- Railway-hosted MySQL database
-- The database is already created by Railway.
USE railway;

-- ============================================================
-- 0. CLEAN RE-RUN
-- ============================================================
DROP VIEW IF EXISTS AVAILABLE_TRAINS;
DROP TRIGGER IF EXISTS trg_before_booking_insert;
DROP TRIGGER IF EXISTS trg_after_booking_insert;
DROP TRIGGER IF EXISTS trg_after_booking_update;
DROP PROCEDURE IF EXISTS book_ticket;
DROP PROCEDURE IF EXISTS expire_old_waitlist;
DROP TABLE IF EXISTS PAYMENT;
DROP TABLE IF EXISTS BOOKING;
DROP TABLE IF EXISTS PASSENGER;
DROP TABLE IF EXISTS TRAIN;

-- ============================================================
-- 1. TABLES (with PK, FK, CHECK, UNIQUE, NOT NULL constraints)
-- ============================================================

CREATE TABLE TRAIN (
    train_id        INT PRIMARY KEY AUTO_INCREMENT,
    train_name      VARCHAR(50) NOT NULL,
    source          VARCHAR(30) NOT NULL,
    destination     VARCHAR(30) NOT NULL,
    total_seats     INT NOT NULL CHECK (total_seats > 0),
    available_seats INT NOT NULL,
    fare            DECIMAL(8,2) NOT NULL CHECK (fare > 0),
    CHECK (available_seats >= 0 AND available_seats <= total_seats)
);

CREATE TABLE PASSENGER (
    passenger_id    INT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(50) NOT NULL,
    age             INT NOT NULL CHECK (age > 0 AND age < 120),
    gender          VARCHAR(10) NOT NULL,
    phone           VARCHAR(15) NOT NULL UNIQUE,
    email           VARCHAR(50) UNIQUE
);

CREATE TABLE BOOKING (
    booking_id      INT PRIMARY KEY AUTO_INCREMENT,
    train_id        INT NOT NULL,
    passenger_id    INT NOT NULL,
    booking_date    DATE NOT NULL DEFAULT (CURRENT_DATE),
    seat_no         INT NOT NULL,
    status          VARCHAR(15) NOT NULL DEFAULT 'CONFIRMED'
                        CHECK (status IN ('CONFIRMED','CANCELLED','WAITLIST')),
    FOREIGN KEY (train_id) REFERENCES TRAIN(train_id) ON DELETE CASCADE,
    FOREIGN KEY (passenger_id) REFERENCES PASSENGER(passenger_id) ON DELETE CASCADE,
    CHECK (seat_no > 0),
    UNIQUE (train_id, seat_no, status)
);

CREATE TABLE PAYMENT (
    payment_id      INT PRIMARY KEY AUTO_INCREMENT,
    booking_id      INT NOT NULL UNIQUE,
    amount          DECIMAL(8,2) NOT NULL CHECK (amount > 0),
    payment_date    DATE NOT NULL DEFAULT (CURRENT_DATE),
    payment_status  VARCHAR(15) NOT NULL DEFAULT 'PENDING'
                        CHECK (payment_status IN ('PENDING','PAID','REFUNDED')),
    FOREIGN KEY (booking_id) REFERENCES BOOKING(booking_id) ON DELETE CASCADE
);

-- ============================================================
-- 2. SAMPLE DATA
-- ============================================================

INSERT INTO TRAIN (train_name, source, destination, total_seats, available_seats, fare) VALUES
('Shatabdi Express', 'Delhi', 'Lucknow', 100, 100, 850.00),
('Rajdhani Express', 'Delhi', 'Mumbai', 120, 120, 1500.00),
('Duronto Express', 'Kolkata', 'Chennai', 90, 90, 1200.00),
('Garib Rath', 'Lucknow', 'Delhi', 150, 150, 450.00),
('Vande Bharat', 'Delhi', 'Varanasi', 80, 80, 1800.00);

INSERT INTO PASSENGER (name, age, gender, phone, email) VALUES
('Amit Sharma', 28, 'Male', '9876543210', 'amit@example.com'),
('Priya Verma', 24, 'Female', '9876543211', 'priya@example.com'),
('Rahul Singh', 35, 'Male', '9876543212', 'rahul@example.com'),
('Sneha Gupta', 30, 'Female', '9876543213', 'sneha@example.com'),
('Vikas Yadav', 40, 'Male', '9876543214', 'vikas@example.com');

-- ============================================================
-- 3. VIEW  (CO1: views/tools)
-- ============================================================

CREATE VIEW AVAILABLE_TRAINS AS
SELECT train_id, train_name, source, destination, available_seats, fare
FROM TRAIN
WHERE available_seats > 0;

-- ============================================================
-- 4. TRIGGERS
-- ============================================================

DELIMITER $$

CREATE TRIGGER trg_before_booking_insert
BEFORE INSERT ON BOOKING
FOR EACH ROW
BEGIN
    DECLARE v_total INT;
    DECLARE v_available INT;

    SELECT total_seats, available_seats
      INTO v_total, v_available
      FROM TRAIN
     WHERE train_id = NEW.train_id;

    IF v_total IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Train not found';
    END IF;

    IF NEW.seat_no > v_total THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Seat number exceeds train capacity';
    END IF;

    IF NEW.status = 'CONFIRMED' AND v_available <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No seats available';
    END IF;
END$$

CREATE TRIGGER trg_after_booking_insert
AFTER INSERT ON BOOKING
FOR EACH ROW
BEGIN
    IF NEW.status = 'CONFIRMED' THEN
        UPDATE TRAIN
        SET available_seats = available_seats - 1
        WHERE train_id = NEW.train_id;
    END IF;
END$$

CREATE TRIGGER trg_after_booking_update
AFTER UPDATE ON BOOKING
FOR EACH ROW
BEGIN
    IF OLD.status = 'CONFIRMED'
       AND (NEW.status = 'CANCELLED' OR NEW.train_id <> OLD.train_id) THEN
        UPDATE TRAIN
        SET available_seats = available_seats + 1
        WHERE train_id = OLD.train_id;
    END IF;

    IF NEW.status = 'CONFIRMED'
       AND (OLD.status <> 'CONFIRMED' OR NEW.train_id <> OLD.train_id) THEN
        UPDATE TRAIN
        SET available_seats = available_seats - 1
        WHERE train_id = NEW.train_id;
    END IF;
END$$

DELIMITER ;

-- 5. STORED PROCEDURE  (CO4: procedures/functions)
-- Books a ticket: checks seat availability, inserts booking,
-- inserts payment record. Trigger above handles seat decrement.
-- ============================================================

DELIMITER $$

CREATE PROCEDURE book_ticket(
    IN p_train_id INT,
    IN p_passenger_id INT,
    IN p_seat_no INT,
    OUT p_booking_id INT,
    OUT p_message VARCHAR(100)
)
BEGIN
    DECLARE v_available INT DEFAULT NULL;
    DECLARE v_fare DECIMAL(8,2) DEFAULT NULL;
    DECLARE v_passenger_exists INT DEFAULT 0;

    SELECT available_seats, fare INTO v_available, v_fare
    FROM TRAIN WHERE train_id = p_train_id;

    SELECT COUNT(*) INTO v_passenger_exists
    FROM PASSENGER WHERE passenger_id = p_passenger_id;

    IF v_available IS NULL THEN
        SET p_message = 'Train not found';
        SET p_booking_id = -1;
    ELSEIF v_passenger_exists = 0 THEN
        SET p_message = 'Passenger not found';
        SET p_booking_id = -1;
    ELSEIF p_seat_no <= 0 THEN
        SET p_message = 'Invalid seat number';
        SET p_booking_id = -1;
    ELSEIF v_available <= 0 THEN
        SET p_message = 'No seats available';
        SET p_booking_id = -1;
    ELSE
        INSERT INTO BOOKING (train_id, passenger_id, seat_no, status)
        VALUES (p_train_id, p_passenger_id, p_seat_no, 'CONFIRMED');

        SET p_booking_id = LAST_INSERT_ID();

        INSERT INTO PAYMENT (booking_id, amount, payment_status)
        VALUES (p_booking_id, v_fare, 'PAID');

        SET p_message = 'Booking confirmed';
    END IF;
END$$

DELIMITER ;

-- ============================================================
-- 6. PROCEDURE WITH CURSOR  (CO4: cursors)
-- Cancels all bookings older than a given date that are still
-- in WAITLIST status (simulates auto-expiry cleanup).
-- ============================================================

DELIMITER $$

CREATE PROCEDURE expire_old_waitlist(IN p_cutoff_date DATE)
BEGIN
    DECLARE v_booking_id INT;
    DECLARE v_done INT DEFAULT 0;

    DECLARE cur CURSOR FOR
        SELECT booking_id FROM BOOKING
        WHERE status = 'WAITLIST' AND booking_date < p_cutoff_date;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    OPEN cur;

    read_loop: LOOP
        FETCH cur INTO v_booking_id;
        IF v_done = 1 THEN
            LEAVE read_loop;
        END IF;

        UPDATE BOOKING SET status = 'CANCELLED' WHERE booking_id = v_booking_id;
    END LOOP;

    CLOSE cur;
END$$

DELIMITER ;

-- ============================================================
-- 7. SAMPLE JOIN QUERIES  (CO3)
-- ============================================================

-- a) Full booking details: passenger + train + payment (3-table join)
-- SELECT p.name, t.train_name, t.source, t.destination, b.seat_no, b.status, pay.amount, pay.payment_status
-- FROM BOOKING b
-- JOIN PASSENGER p ON b.passenger_id = p.passenger_id
-- JOIN TRAIN t ON b.train_id = t.train_id
-- LEFT JOIN PAYMENT pay ON pay.booking_id = b.booking_id;

-- b) Revenue per train
-- SELECT t.train_name, SUM(pay.amount) AS total_revenue
-- FROM TRAIN t
-- JOIN BOOKING b ON t.train_id = b.train_id
-- JOIN PAYMENT pay ON pay.booking_id = b.booking_id
-- WHERE pay.payment_status = 'PAID'
-- GROUP BY t.train_name;

-- c) Passengers with no bookings (subquery)
-- SELECT name, phone FROM PASSENGER
-- WHERE passenger_id NOT IN (SELECT passenger_id FROM BOOKING);


-- ============================================================
-- 8. VERIFICATION OUTPUT
-- ============================================================
SELECT train_id, train_name, source, destination, total_seats, available_seats, fare
FROM TRAIN ORDER BY train_id;

SELECT passenger_id, name, age, gender, phone, email
FROM PASSENGER ORDER BY passenger_id;

SELECT COUNT(*) AS total_trains FROM TRAIN;
SELECT COUNT(*) AS total_passengers FROM PASSENGER;
SELECT COUNT(*) AS total_bookings FROM BOOKING;
SELECT COUNT(*) AS total_payments FROM PAYMENT;
