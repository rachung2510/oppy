#include "ICM20600.h"
#include <Wire.h>
#include <SD.h>
#include <SPI.h>

ICM20600 icm20600(true);

#define FLEXPIN0 A0
#define FLEXPIN1 A1
#define FLEXPIN2 A2
#define FLEXPIN3 A3
#define SDA A4
#define SCL A5
#define SD_ChipSelectPin 10

// flex sensor values
float indexVal;
float middleVal;
float ringVal;
float pinkyVal;

// IMU values
int accX;
int accY;
int accZ;
int sound_activated = 0; // boolean
int ACC_THRESH_LOW = -1500;
int ACC_THRESH_HIGH = -600;
int counter = 0;

// writing to SD card
int STORE_DATA = 0; // boolean
File dataFile;
String filename = "acc_data.csv";

void setup() {
  // put your setup code here, to run once:

  // join I2C bus (I2Cdev library doesn't do this automatically)
  Wire.begin();

  Serial.begin(9600);
  icm20600.initialize();
  // icm20600.reset();
  // Serial.print("DeviceID: ");
  // Serial.println(icm20600.getDeviceID(), HEX);

  // Check if the card is present and can be initialized:
  if (STORE_DATA == 1) {
    if (!SD.begin(SD_ChipSelectPin)) {  
      Serial.println("SD fail");  
      return;
    }
    // Serial.println("SD ok");
    dataFile = SD.open(filename, FILE_WRITE);  
    dataFile.println("accX,accY,accZ");
  }

}

void loop() {
  // put your main code here, to run repeatedly:

  // indexVal = analogRead(FLEXPIN0);
  // middleVal = analogRead(FLEXPIN1);
  // ringVal = analogRead(FLEXPIN2);
  // pinkyVal = analogRead(FLEXPIN3);

  // Serial.print("Index:");
  // Serial.print(indexVal);
  // Serial.print(",Middle:");
  // Serial.println(middleVal);

  accX = icm20600.getAccelerationX();
  accY = icm20600.getAccelerationY();
  accZ = icm20600.getAccelerationZ();

  // Serial.print("accX:");
  // Serial.println(accX);
  // Serial.print(",accY:");
  // Serial.print(accY);
  // Serial.print(",accZ:");
  // Serial.println(accZ);
  
  if (STORE_DATA == 0) {
    if ((accX < ACC_THRESH_LOW) && (sound_activated == 0)) {
      counter++;
      Serial.print("PLAY SOUND ");
      Serial.println(counter);
      sound_activated = 1;
    }
    if (accX > ACC_THRESH_HIGH) {
      sound_activated = 0;
    }
  } else {
    // write to CSV
    dataFile.print(accX);
    dataFile.print(",");
    dataFile.print(accY);
    dataFile.print(",");
    dataFile.println(accZ);  

    if (Serial.available()) {
      if (Serial.read() == 'q') {
        dataFile.close();
      }
    }
  }

}

