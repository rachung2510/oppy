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
int sound_over = 1; // boolean
int ACC_THRESH = 600;
int counter = 0;

// RX, TX for Bluetooth
SoftwareSerial btSerial(RxD, TxD);

void setup() {
  // put your setup code here, to run once:

  Wire.begin(); // join I2C bus
  Serial.begin(115200);

  icm20600.initialize();
  
}

void loop() {
  // put your main code here, to run repeatedly:

  indexVal = analogRead(FLEXPIN2);
  middleVal = analogRead(FLEXPIN3);
  ringVal = analogRead(FLEXPIN1);
  pinkyVal = analogRead(FLEXPIN0);
  accX = icm20600.getAccelerationX();

  // ====== For PuTTY / Bluetooth ======
  Serial.print(indexVal);
  Serial.print(",");
  Serial.print(middleVal);
  Serial.print(",");
  Serial.print(ringVal);
  Serial.print(",");
  Serial.print(pinkyVal);
  Serial.print(",");
  Serial.print(accX);
  Serial.println(",");

  // ====== For Serial Plotting ======
//   Serial.print("Index:");
//   Serial.print(indexVal);
//   Serial.print(",Middle:");
//   Serial.print(middleVal);
//   Serial.print(",Ring:");
//   Serial.print(ringVal);
//   Serial.print(",Pinky:");
//   Serial.print(pinkyVal);
//
//   Serial.print(",accX:");
//   Serial.println(accX);
  
  // ====== Accelerometer thresholding ======
//  if ((accX > ACC_THRESH) && (sound_over == 1)) {
//    sound_over = 0;
//    sound_activated = 1;
//  }
//  if ((accX < ACC_THRESH) && (sound_activated == 1)) {
//    sound_activated = 0;
//    counter++;
////    Serial.print("PLAY SOUND ");
////    Serial.println(counter);
//  }
//  if (accX < -2500) {
//    sound_over = 1;
//  }


}
