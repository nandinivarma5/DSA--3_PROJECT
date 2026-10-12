#!/bin/bash
mkdir -p bin
javac -d bin src/*.java && java -cp bin Main
