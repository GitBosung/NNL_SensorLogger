# Sensor Data Logger App

## Overview
This Android application logs smartphone sensor data at **50Hz** and GPS data at **1Hz**, storing the collected data as CSV files in the **Downloads/SensorData** directory. The application is designed for accurate sensor data collection and analysis.

## Features
- 📡 **Logs Sensor Data at 50Hz**: Collects accelerometer, gyroscope, magnetometer, pressure, and game rotation vector data.
- 📍 **Logs GPS Data at 1Hz**: Captures location, altitude, and speed.
- 📝 **Saves Data as CSV Files**: Files are stored in the `Downloads/SensorData` folder.
- 🏷 **Custom File Naming**: Users can input a custom filename before stopping data logging.
- 🔄 **Automatic Sensor & GPS Handling**: Starts logging as soon as GPS data is available.

## Sensors Used
- **Accelerometer (TYPE_ACCELEROMETER)**: Measures acceleration in X, Y, Z axes.
- **Gyroscope (TYPE_GYROSCOPE)**: Measures angular velocity.
- **Magnetometer (TYPE_MAGNETIC_FIELD)**: Measures magnetic field strength.
- **Game Rotation Vector (TYPE_GAME_ROTATION_VECTOR)**: Provides device orientation.
- **Pressure Sensor (TYPE_PRESSURE)**: Measures atmospheric pressure.
- **GPS (LocationManager)**: Captures latitude, longitude, altitude, and speed.

## File Structure
- **CSV File Format:**
  ```csv
  Time,Accel_X,Accel_Y,Accel_Z,Gyro_X,Gyro_Y,Gyro_Z,Mag_X,Mag_Y,Mag_Z,Orient_X,Orient_Y,Orient_Z,Pressure,Latitude,Longitude,Altitude,Speed
  2025-03-08 12:34:56.789,0.01,0.02, ..... 
