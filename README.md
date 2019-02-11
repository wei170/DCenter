# DCenter
Drones central control and network analyzing

## Motivation
Over the past few years, drones have become central to the functions of various businesses and governmental organizations and have managed to pierce through areas where certain industries were either stagnant or lagging behind. Improving accuracy	and refining service of the commercial drones inspire more business functionalities. However, almost all the commercial drones are controlled with the remote controller via the Wifi connection, which highly restricts the scope of the mobility and scale of discovery. If the drone can be connected to the Internet via the cellular, the restriction of the distance between the drone and the remote controller can be freed. Meanwhile, we can control and survey all drones in a centralized platform, like all other IoTs.

## Problem
It would be great to install a SIM card in the drone and develop an app on top of that to send all network (i.e. LTE packets) and drone data (i.e. location, flying mode, speed, etc) to the server. However, it is hard to do the hardware hack.

## State of the art
Attaching a smartphone to the drone is the easiest way to connect to the cellular network. The DJI open-source SDK is able to control all functionalities on the smartphone. Moreover, MobileInsight works perfectly on the smartphone to collect all the network packets. 

## Goals
The goal of this project is to develop an Android app that works as an intermediation between the server and the drone via the cellular connection. The app controls the drone via the Wifi, sends all the information of the drone, including GPS location, flying mode and speed, and MobileInsight online analyzing logs to the server. The server is able to send flying commands to the drones and virtualize the real-time information of all the on-mission drones. 

## Challenges
The signal of the cellular is low in the air and can be interfered by the neighbor base station. LOS probability varies in different areas. Both could cause a higher latency and a lower throughput.

## Plans
* Develop an Android app that can control the drone directly.
* Setup a server to stream commands to the app.
* Figure out a way to stream all the drone flying information to the server.
* Build a simple web app to virtualize the information of the drones.
* Enable MobileInsight to online analyzing network logs on the server.
