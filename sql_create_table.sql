CREATE TABLE gesture_data (
	`id` int NOT NULL AUTO_INCREMENT,
    `date` varchar(50) NOT NULL,
    `index` double NOT NULL,
    `middle` double NOT NULL,
    `ring` double NOT NULL,
    `pinky` double NOT NULL
    `accX` double,
    `accY` double,
    `accZ` double,
    `gesture` varchar(5),
    PRIMARY KEY (id)
)
