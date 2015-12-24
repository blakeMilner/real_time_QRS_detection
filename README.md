# real_time_QRS_detection
A real-time QRS detection algorithm for the Arduino platform - based on the Pan-Tompkins Algorithm. This is a simplification of the aforementioned algorithm, described in the paper:

HC Chen, SW Chen, "A Moving Average based Filtering System with its Application to Real-time QRS Detection", Computers in Cardiology, 2003.


# Files
* QRS_test.java - The original, pseudo-real-time implementation of the detection algorithm, used as a starting point for the subsequent 2 files. This was taken from the repo: https://github.com/cc79128/QRS_algorithm
* QRS_test.c - A generic C implentation of the real-time algorithm, meant to be ran offline (on a PC or laptop). The purpose of this program is to test changes/improvements to the base algorithm before testing on actual embedded platform.
* QRS_arduino/QRS.ino - The real-time implementation of the QRS detection algorithm, designed for use on an Arduino Uno using the SparkFun AD8232 Single-Lead Heart Rate Monitor (https://www.sparkfun.com/products/12650).
