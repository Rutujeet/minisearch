# MiniSearch

MiniSearch is a full-text search engine learning project written in Java.

## Current functionality

Currently MiniSearch can:

- hold a small collection of documents in memory
- perform case-insensitive keyword searches
- return matching documents using a linear scan

## Build

./gradlew build

## Test

./gradlew test

## Run

./gradlew run

## Current limitations

Search scans every document for every query.

Tokenization currently only handles basic whitespace-separated words.
