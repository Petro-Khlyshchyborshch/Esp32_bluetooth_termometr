# Bluetooth термометр
## Android програма
<p>Розпочнемо з аndroid програми. Для створення Adroid програм компанія Google створила власне ide – Android Studio. Най ефективніша та найзручніша ide для розробки Adroid програм, розуміє з пів слова(більшість однотипних, або часто використовуваних функцій оголошуються та створюється «каркас» автоматично, це ж саме можна сказати про конструктори класів та перевизначення абстрактних функцій).</p>
<p>З вибором середовища розробки розібрались, а яку мову використовувати? Існує 2 варіанти – Java та Kotlin. Ці дві мови дуже схожі між собою (якщо розмовляти про Adroid розробку), проте у Java є перевага – велика кількість навчального матеріалу, так як існує набагато довше ніж Kotlin, тому як початківцю, вибір припав саме на Java.</p>
<p>В програмі використаємо  явний MAC-адрес пристрою, до якого ми будемо підключатися, це значно спростить написання програми. Дізнатися MAC-адрес можна, наприклад, в меню налаштувань блютуз вашого аndroid присторою. Основним завданням є налагодити двох-стороній канал зв'язку між ESP32 та аndroid пристроєм, тобто організувати отримання/передачу даних між пристроями. Основна проблема виникає в отриманні данних, оскільки в андроїді для прийому даних від будь-якого пристрою необхідно створювати окремий фоновий потік, щоб у нас не зависало основне activity. Для цього ми використовуємо клас thread і всі дані будуть прийматися в окремому потоці. На вікно головного activity ми додамо новий елемент TextView, який буде служити для відображення отриманих даних від ESP32, а також ImageButton, що буде використовуватись для відправки даних на ESP32 по натисканню. </p>
<p>В першу чергу потрібно забезпечити передачу данних між фоновим потоком для робити з bluetooth і головною activity . Для такого роду проблем застосовують клас Handler – він використовує свій потік, який працює незалежно від головної програми, що дає змогу звертатись до нього з будь-яких інших потоків. Клас Handler містить у собі функцію handleMessage, яка викликається, коли Handler отримує повідомлення(данні). Тому, ми можемо цю функцію перевизначити, додавши власний функціонал.</p>
<p>Код:</p>

```java
h = new Handler() {//хендлер дозволяє передавати данні між різними потоками(в данному випадку між потоком Bluetooth та основною програмою
    public void handleMessage(android.os.Message msg) {
       if(msg.what== RECIEVE_MESSAGE){// якщо оримали повідомлення
                byte[] readBuf = (byte[]) msg.obj; // записуємо отримайний масив байтів(asci кодів)
                String strIncom = new String(readBuf, 0, msg.arg1);//формуємо повідомлення у стрічку
                txtArduino.setText( strIncom);//обовляємо текст
                btn.setEnabled(true);//вмикає натискання кнопки
        }
    };
};

```

<p>
Після реалізації Handler  постає не менш складне завдання – розробити логіку роботи фонового процесу. Для вирішення цього завдання стрворимо з унаслідування від Thread новий клас ConnectedThread, у якому перевизначимо основні функції, які потрібні для роботи фонового потоку із потрібним нам функціоналом.  Слід не забувати, що нам потрібно отримані данні відправити в головне activity через Handler.
</p>

<p>Код класу ConnectedThread:</p>

```java
private class ConnectedThread extends Thread {
    //створюємо клас для каналу звязку(потоку) по Bluetooth із унаслідуваням від стандартного класу потоків
    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;

    public ConnectedThread(BluetoothSocket socket) {
        mmSocket = socket;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) {
            errorExit();
        }

        mmInStream = tmpIn;
        mmOutStream = tmpOut;
    }
    //оримання повідомлення
    public void run() {
        byte[] buffer = new byte[256];
        int bytes;

        while (true) {
            try {
                bytes = mmInStream.read(buffer);
                h.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();
            } catch (IOException e) {
                break;
            }
        }
    }

    //передача повідомлення
    public void write(String message) {

        byte[] msgBuffer = message.getBytes();
        try {
            mmOutStream.write(msgBuffer);
        } catch (IOException e) {
            errorExit();
        }
    }

    //вимкнення зв'язку
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) {
            errorExit();
    }
    }
}

```
<p>Тепер ініціалізуємо всі зміні та класи, робити ми будемо це в більшості у функції onCreate() – етап ініціалізації елементів activity. Тут і визначемо обробку натискань на ImageButton – відправка «1» по bluetooth.</p>

```java
ImageButton btn;
TextView txtArduino;
Handler h;
private static final int REQUEST_ENABLE_BT = 1;
final int RECIEVE_MESSAGE = 1;        // Статус для Handler
private BluetoothAdapter btAdapter = null;
private BluetoothSocket btSocket = null;
private OutputStream outStream = null;
private ConnectedThread mConnectedThread;

// SPP UUID андроід присторою (випадкові числа у відповідній сторуктурі)
private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

// MAC-адрес Bluetooth модуля
private static String address = "3C:71:BF:6B:D2:92";

@SuppressLint("HandlerLeak")
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    btn=findViewById(R.id.imageButton);

    txtArduino = (TextView) findViewById(R.id.txtArduino);
    h = new Handler() {//хендлер дозволяє передавати данні між різними потоками(в данному випадку між потоком Bluetooth та основною програмою
        public void handleMessage(android.os.Message msg) {
           if(msg.what== RECIEVE_MESSAGE){// якщо оримали повідомлення
                    byte[] readBuf = (byte[]) msg.obj; // записуємо отримайний масив байтів(asci кодів)
                    String strIncom = new String(readBuf, 0, msg.arg1);//формуємо повідомлення у стрічку
                    txtArduino.setText( strIncom);//обовляємо текст
                    btn.setEnabled(true);//вмикає натискання кнопки
            }
        };
    };

    btAdapter = BluetoothAdapter.getDefaultAdapter();//строруюмо блютуз адаптер
    checkBTState();//перевіряємо статус Bluetooth

    btn.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {//оброблювач натискання на кнопку
            mConnectedThread.write("1");//відправляємо на пристрій 1
            btn.setEnabled(false);// вимикаємо нажаття кнопки, щоб не відправляти паралейно данні з esp32
        }
    });

}

```
<p>
Підкючення bluetooth виконаємо в  onResume() – це функція що в ходить у список функцій життєвого циклу activity, і викликається перед появою елементів activity, проте після onCreate(), де відбулась ініціалізація. Цю функцію вибрано для того, щоб зменшити час підключення(не потрібно спочатку створювати інтерфейс) та в разі невдачі швидше вийти з програми.
</p>

<p>Код onResume() та залежних функцій:</p>

```java
public void onResume() {
    super.onResume();
    BluetoothDevice device = btAdapter.getRemoteDevice(address);//зберігаємо вказівник на блютуз пристрій за його адресом
    //для встановлення зв'язку потрібний мас-адрес та UUID
    try {
        btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
    } catch (IOException e) {
        errorExit();
    }

    btAdapter.cancelDiscovery();//вимкнемо пошук блютуз пристороїв

    try {
        btSocket.connect();//підключення
    } catch (IOException e) {
        try {
            btSocket.close();//якщо відбулося невдале підключенння, то припиняємо  з'єднання
        } catch (IOException e2) {
            errorExit();//вихід з програми
        }
    }

    //створюємо канал зв'язку
    mConnectedThread = new ConnectedThread(btSocket);
    mConnectedThread.start();

}
private void checkBTState() {
    //перевіряємо чи є Bluetooth і чи він вімкнений
    if(btAdapter==null) {
        errorExit();
    } else {
        if (!(btAdapter.isEnabled())) {
            //якщо вимкнений то робимо запит на включення
            Intent enableBtIntent = new Intent(btAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }
}

private void errorExit(){
    Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();//повідомлення про помилку
    finish();//закриття програми
}

```
##Код для ESP32
<p>Друга частина виявилась дещо простішою, вона складається з 2 етапів, перший – підключити до ESP32 датчик температури та вологи. Ми вирішили використовувати DHT11 із-за його дешевизни і простоті роботи з ним.</p>
<p>Схема підключення на рисунку:</p>

![](https://github.com/petro228/Esp32_bluetooth_termometr/blob/main/Esp32_bluetooth_termometr/Photo/Schem.jpg)

<p>Для стабільного зчитування даних з DHT11 потрібно підтягути лінію даних (2 нога) до живлення через резистор 10 КОм.</p>
<p>Утворений пристрій буде отримувати живлення безпосередньо від USB порта комп’ютера через роз’єм для прошивки та налагодження.</p>
<p>2 етап - сама програма для ESP32. Для розробки програм під ESP32 існує декілька IDE, проте використовуватиму найбільш простішу, славнозвісну Arduino IDE, так як її функціоналу повністю вистачає для реалізації нашої програми, при чому є можливість швидкої відладки через USB.</p>
<p>Все працює дуже просто – ми в головному циклі завжди перевіряємо наявність повідомлення bluetooth, і якщо ми його отримуємо то перевіряємо чи це те що нам потрібно («1»), слід не забувати що по bluetooth данні передаються в ASCI кодах (наша «1» це код 49).</p>
<p>Також для спрощення програми було використано 2 бібліотеки - BluetoothSerial.h для роботи з блютуз, та DHT.h для роботи з датчиком температури та вологості DHT11.</p>
<p>Код для ESP32:</p>

```C
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

```

<p>Ось як виглядає програма на Android пристрої:</p>

![](https://github.com/petro228/Esp32_bluetooth_termometr/blob/main/Esp32_bluetooth_termometr/Photo/android.png)





