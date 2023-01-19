#include "ICM20600.h"
#include <Wire.h>

ICM20600 icm20600(true);

#define FLEXPIN0 A0
// #define FLEXPIN1 A1
// #define FLEXPIN2 A2
// #define FLEXPIN3 A3

#define SDA A4
#define SCL A5

float indexVal;
float middleVal;
float ringVal;
float pinkyVal;

void setup() {
  // put your setup code here, to run once:

  // join I2C bus (I2Cdev library doesn't do this automatically)
  Wire.begin();

  Serial.begin(9600);
  icm20600.initialize();
  // icm20600.reset();

  Serial.print("DeviceID: ");
  Serial.println(icm20600.getDeviceID(), HEX);

}

void loop() {
  // put your main code here, to run repeatedly:

  indexVal = analogRead(FLEXPIN0);
  middleVal = analogRead(FLEXPIN1);
  ringVal = analogRead(FLEXPIN2);
  pinkyVal = analogRead(FLEXPIN3);

  printVal("Index: ", indexVal, "");
  printVal("Middle: ", middleVal, "");
  printVal("Ring: ", ringVal, "");
  printVal("Pinky: ", pinkyVal, "");
  Serial.println("");
  printImuVals(); // print IMU values

  Serial.println("-------------------------");
  delay(2000);

}

void printVal(String name, float val, String unit) {
  Serial.print(name);
  Serial.print(": ");
  Serial.print(val, 10);
  Serial.print(" ");
  Serial.println(unit);
}

void printImuVals() {
  Serial.println("Acceleroscope");
  printVal("X", icm20600.getAccelerationX(), "mg");
  printVal("Y", icm20600.getAccelerationY(), "mg");
  printVal("Z", icm20600.getAccelerationZ(), "mg");

  Serial.println("Gyroscope");
  printVal("X", icm20600.getGyroscopeX(), "dps");
  printVal("Y", icm20600.getGyroscopeY(), "dps");
  printVal("Z", icm20600.getGyroscopeZ(), "dps");
}
