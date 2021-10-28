#include "BluetoothSerial.h"
#include <DHT.h>
#define DHTPIN 15 //вхід мікроконтролера до якго підключений датчик
#define DHTTYPE DHT11 //тип датчика
BluetoothSerial ESP_BT; // об'єкт для роботи з bluetooth
DHT dht(DHTPIN, DHTTYPE);//обєкт для роботи з датчиком
int incoming;
float h;
float t;
void setup() {
  ESP_BT.begin("ESP32_Meteo_station"); // задаємо ім'я Bluetooth пристрою
  dht.begin();//запуск роботи датчика
}

   void loop() {
        if (ESP_BT.available()) //перевіряємо чи щось отримали по Bluetooth
        {
        	   incoming = ESP_BT.read(); // читаєм , що отримали
           if (incoming == 49)  // якщо це "1" 
              {
                h = dht.readHumidity();
        		//зчитуємо показники волги
       		t = dht.readTemperature();
        		//зчитуємо показники температури
        		if (isnan(h) || isnan(t)) {//перевірка на роботу датчика
        		ESP_BT.print("Помилка вимірувань");
        		return;
        }
        //відпрака даннних
        ESP_BT.print(" ");
        ESP_BT.print("Температура: ");
        ESP_BT.println(t);
        ESP_BT.print("Волога: ");
        ESP_BT.print(h);

               }
       }
   delay(1000);//затримка в 1 секунду для нормальної робити датчика DHT11
}
