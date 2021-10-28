package com.example.blutooth_exempls
import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

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
        h = new Handler() {//хендлер дозволяє передавати данні між різними потоками(в данному випадку між потоком блютуз та основною програмою
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
        checkBTState();//перевіряємо статус блютузу

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {//оброблювач натискання на кнопку
                mConnectedThread.write("1");//відправляємо на пристрій 1
                btn.setEnabled(false);// вимикаємо нажаття кнопки, щоб не відправляти паралейно данні з ардуіно
            }
        });

    }
    @Override
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

    @Override
    public void onPause() {
        //коли програма знаходиться в паузі
        // (вийшли з програми на головне меню, чи пеерейшли в інше програму, при цьому не вигрузивши данні з ОП)
        super.onPause();
        try     {
            btSocket.close();
        } catch (IOException e2) {
            errorExit();
        }
    }

    private void checkBTState() {
        //перевіряємо чи є блютуз і чи він вімкнений
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

    private class ConnectedThread extends Thread {
        //створюємо клас для каналу звязку(потоку) по блютуз із унаслідуваням від стандартного класу потоків
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
}
