CREATE TABLE gesture_data (
	`id` int NOT NULL AUTO_INCREMENT,
	`trial` int,
    `date` varchar(50) NOT NULL,
    `index` double NOT NULL,
    `middle` double NOT NULL,
    `ring` double NOT NULL,
    `pinky` double NOT NULL,
	`beat` int,
    `gesture` varchar(5),
    PRIMARY KEY (id)
)
