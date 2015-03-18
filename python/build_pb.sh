#!/bin/bash
#
# creates the python classes for our .proto
#
project_base="/Users/minu/Desktop/SJSU/275/core-netty-4.2"

#project_base="/Users/gash/workspace/messaging/core-netty/python"

rm ${project_base}/python/src/comm_pb2.py

# protoc -I=${project_base}/resources --python_out=./src ../resources/comm.proto
# protoc -I=./resources --python_out=./src ./resources/comm.proto
# protoc -I=./resources --python_out=./src ./resources/comm.proto
protoc -I=${project_base}/resources --python_out=./src ${project_base}/resources/comm.proto