
// Stack Universal

#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>

// WiFi credentials
const char* ssid = "";
const char* password = "";

// HiveMQ Cloud credentials
const char* mqtt_server = "";
const int mqtt_port = ;
const char* mqtt_user = "";
const char* mqtt_pass = "";

// MQTT topics
const char* topic_relay = "";
const char* topic_schedule = "";
const char* topic_status = "";
const char* topic_reset = "";

#define RELAY_PIN D2
#define LED_PIN LED_BUILTIN

WiFiClientSecure espClient;
PubSubClient client(espClient);

String scheduledDate = "";
int onHour = 0, onMinute = 0;
int offHour = 0, offMinute = 0;
bool jadwalAktif = false;
bool relayState = false;
unsigned long lastStatusPublish = 0;

void getCurrentDateTime(int& year, int& month, int& day, int& hour, int& minute) {
  time_t now = time(nullptr);
  struct tm* t = localtime(&now);
  year = t->tm_year + 1900;
  month = t->tm_mon + 1;
  day = t->tm_mday;
  hour = t->tm_hour;
  minute = t->tm_min;
}

bool isWithinSchedule() {
  int year, month, day, hour, minute;
  getCurrentDateTime(year, month, day, hour, minute);
  char buf[16];
  sprintf(buf, "%04d-%02d-%02d", year, month, day);
  String today = String(buf);
  if (today != scheduledDate) return false;
  int nowMinutes = hour * 60 + minute;
  int onMinutes = onHour * 60 + onMinute;
  int offMinutes = offHour * 60 + offMinute;
  return (nowMinutes >= onMinutes && nowMinutes < offMinutes);
}

void publishStatus() {
  StaticJsonDocument<64> doc;
  doc["status"] = relayState ? "ON" : "OFF";
  doc["schedule"] = jadwalAktif ? "auto" : "manual";
  doc["source"] = (jadwalAktif && isWithinSchedule()) ? "auto" : "manual";
  char buf[64];
  serializeJson(doc, buf);
  client.publish(topic_status, buf);
}

void callback(char* topic, byte* payload, unsigned int length) {
  payload[length] = '\0';
  String message = String((char*)payload);

  if (String(topic) == topic_relay) {
    if (message == "ON") {
      relayState = true;
      digitalWrite(RELAY_PIN, HIGH);
      digitalWrite(LED_PIN, LOW);
      publishStatus();
    } else if (message == "OFF") {
      relayState = false;
      digitalWrite(RELAY_PIN, LOW);
      digitalWrite(LED_PIN, HIGH);
      publishStatus();
    }
  } else if (String(topic) == topic_schedule) {
    StaticJsonDocument<128> doc;
    DeserializationError error = deserializeJson(doc, message);
    if (!error) {
      scheduledDate = String(doc["date"].as<const char*>());
      String onStr = doc["on"].as<const char*>();
      String offStr = doc["off"].as<const char*>();
      onHour = onStr.substring(0, 2).toInt();
      onMinute = onStr.substring(3, 5).toInt();
      offHour = offStr.substring(0, 2).toInt();
      offMinute = offStr.substring(3, 5).toInt();
      jadwalAktif = true;
      publishStatus();
    }
  } else if (String(topic) == topic_reset) {
    jadwalAktif = false;
    publishStatus();
  }
}

void reconnect() {
  while (!client.connected()) {
    if (client.connect("WemosClient", mqtt_user, mqtt_pass)) {
      client.subscribe(topic_relay);
      client.subscribe(topic_schedule);
      client.subscribe(topic_reset);
    } else {
      delay(5000);
    }
  }
}

void setup() {
  pinMode(RELAY_PIN, OUTPUT);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, LOW);
  digitalWrite(LED_PIN, HIGH);
  Serial.begin(115200);

  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected");

  configTime(8 * 3600, 0, "pool.ntp.org", "time.nist.gov");

  espClient.setInsecure();
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);
}

void loop() {
  if (!client.connected()) {
    reconnect();
  }
  client.loop();

  // Penjadwalan otomatis
  if (jadwalAktif) {
    if (isWithinSchedule()) {
      if (!relayState) {
        relayState = true;
        digitalWrite(RELAY_PIN, HIGH);
        digitalWrite(LED_PIN, LOW);
        publishStatus();
      }
    } else {
      if (relayState) {
        relayState = false;
        digitalWrite(RELAY_PIN, LOW);
        digitalWrite(LED_PIN, HIGH);
        publishStatus();
      }
      // Reset jadwalAktif setelah jadwal selesai
      int year, month, day, hour, minute;
      getCurrentDateTime(year, month, day, hour, minute);
      int nowMinutes = hour * 60 + minute;
      int offMinutes = offHour * 60 + offMinute;
      if (nowMinutes >= offMinutes) {
        jadwalAktif = false;
        publishStatus();
      }
    }
  }

  // Publish status setiap 5 detik
  if (millis() - lastStatusPublish > 5000) {
    publishStatus();
    lastStatusPublish = millis();
  }
}
