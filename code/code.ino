#include "ICM20600.h"
#include <Wire.h>
#include <SoftwareSerial.h>

ICM20600 icm20600(true);

#define FLEXPIN0 A0
#define FLEXPIN1 A1
#define FLEXPIN2 A2
#define FLEXPIN3 A3
#define SDA A4
#define SCL A5
#define TxD 2
#define RxD 3

// flex sensor values
float indexVal;
float middleVal;
float ringVal;
float pinkyVal;

// IMU values
int accX;
int accY;
int accZ;

// IMU thresholding
int sound_activated = 0; // boolean
int ACC_THRESH_LOW = -1500;
int ACC_THRESH_HIGH = -600;
int counter = 0;

// RX, TX for Bluetooth
SoftwareSerial btSerial(RxD, TxD);

void setup() {
  // put your setup code here, to run once:

  Wire.begin(); // join I2C bus
  btSerial.begin(9600);

  icm20600.initialize();
  // Serial.println("Index,Middle,Ring,Pinky,accX,accY,accZ"); // for CSV
}

void loop() {
  // put your main code here, to run repeatedly:

  indexVal = analogRead(FLEXPIN2);
  middleVal = analogRead(FLEXPIN3);
  ringVal = analogRead(FLEXPIN1);
  pinkyVal = analogRead(FLEXPIN0);
  accX = icm20600.getAccelerationX();
  accY = icm20600.getAccelerationY();
  accZ = icm20600.getAccelerationZ();
  
  // ====== For PuTTY / Bluetooth ======
  btSerial.print(indexVal);
  btSerial.print(",");
  btSerial.print(middleVal);
  btSerial.print(",");
  btSerial.print(ringVal);
  btSerial.print(",");
  btSerial.println(pinkyVal);
  // btSerial.print(",");
  // btSerial.print(accX);
  // btSerial.print(",");
  // btSerial.print(accY);
  // btSerial.print(",");
  // btSerial.println(accZ);

  // ====== For Serial Plotting ======
  // Serial.print("Index:");
  // Serial.print(indexVal);
  // Serial.print(",Middle:");
  // Serial.print(middleVal);
  // Serial.print(",Ring:");
  // Serial.print(ringVal);
  // Serial.print(",Pinky:");
  // Serial.println(pinkyVal);

  // Serial.print("accX:");
  // Serial.println(accX);
  // Serial.print(",accY:");
  // Serial.print(accY);
  // Serial.print(",accZ:");
  // Serial.println(accZ);
  
  // ====== Accelerometer thresholding ======
  // if ((accX < ACC_THRESH_LOW) && (sound_activated == 0)) {
  //   counter++;
  //   Serial.print("PLAY SOUND ");
  //   Serial.println(counter);
  //   sound_activated = 1;
  // }
  // if (accX > ACC_THRESH_HIGH) {
  //   sound_activated = 0;
  // }

}

