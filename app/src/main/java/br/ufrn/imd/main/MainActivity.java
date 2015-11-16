package br.ufrn.imd.main;

import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.cardiomood.android.controls.gauge.SpeedometerGauge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private TextView txtBluetooth;
    private TextView txtNome;
    private TextView txtMac;
    private TextView txtSituacao;

    private TextView txtPressao;
    private TextView txtRpm;
    private TextView txtVelocidade;
    private TextView txtTemperatura;
    private TextView txtFluxo;

    private SpeedometerGauge speedometer;

    private Button btnLigar;
    private Button btnListar;

    private String stLigado;
    private String nome;
    private String mac;
    private int situacao;

    private static final int REQUEST_ENABLE_BT = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;

    private BluetoothAdapter myBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothSocket mBluetoothSocket;

    private ConnectedThread mConnectedThread;
    private ExecuteThread mExecuteThread;

    private ArrayAdapter<String> BTArrayAdapter;

    public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TAG = "ObdBluetooth";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        carregarTela();
    }

    public void carregarTela() {
        setContentView(R.layout.activity_main);

        txtBluetooth = (TextView) findViewById(R.id.bluetooth);
        txtNome = (TextView) findViewById(R.id.nome);
        txtMac = (TextView) findViewById(R.id.mac);
        txtSituacao = (TextView) findViewById(R.id.situacao);

        txtPressao = (TextView) findViewById(R.id.pressao);
        txtRpm = (TextView) findViewById(R.id.rpm);
        txtVelocidade = (TextView) findViewById(R.id.velocidade);
        txtTemperatura = (TextView) findViewById(R.id.temperatura);
        txtFluxo = (TextView) findViewById(R.id.fluxo);

        txtPressao.setText("0 bar");
        txtRpm.setText("0");
        txtVelocidade.setText("0 km/h");
        txtTemperatura.setText("0º C");
        txtFluxo.setText("0 m³/min");

        speedometer = (SpeedometerGauge) findViewById(R.id.speedometer);
        speedometer.setLabelConverter(new SpeedometerGauge.LabelConverter() {
            @Override
            public String getLabelFor(double progress, double maxProgress) {
                return String.valueOf((int) Math.round(progress));
            }
        });
        speedometer.setMaxSpeed(300);
        speedometer.setMajorTickStep(30);
        speedometer.setMinorTicks(5);
        speedometer.addColoredRange(0, 60, Color.GREEN);
        speedometer.addColoredRange(60, 110, Color.YELLOW);
        speedometer.addColoredRange(110, 300, Color.RED);

        btnLigar = (Button) findViewById(R.id.btnLigar);
        btnListar = (Button) findViewById(R.id.btnFind);

        myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (myBluetoothAdapter == null) {
            btnLigar.setEnabled(false);
            btnListar.setEnabled(false);
            txtBluetooth.setText("Não suportado");

            Toast.makeText(getApplicationContext(), "Seu dispositivo não suporta bluetooth.", Toast.LENGTH_LONG).show();
        } else {
            if (myBluetoothAdapter.isEnabled()) {
                stLigado = "Ligado";
                btnLigar.setText("Desligar Bluetooth");
            } else{
                stLigado = "Desligado";
            }
            txtBluetooth.setText(stLigado);

            BTArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);

            btnLigar.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    ligarDesligar(view);
                }
            });
            btnListar.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    localizar(view);
                }
            });
        }
    }

    final BroadcastReceiver bReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // add the name and the MAC address of the object to the arrayAdapter
                BTArrayAdapter.add(device.getBondState() + " | " + device.getName() + " | " + device.getAddress());
                BTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    public void obdBluetooth(){
        Log.i(TAG, "Pegando dispositivo remoto");
        mBluetoothDevice = myBluetoothAdapter.getRemoteDevice(mac);
        Log.i(TAG, mBluetoothDevice.getName());

        if (mBluetoothDevice == null) {
            Toast.makeText(getApplicationContext(), "Não foi possivel se conectar", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Log.i(TAG, "createRfcommSocketToServiceRecord");
            mBluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "Erro ao criar createRfcommSocketToServiceRecord", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        try {
            Log.i(TAG, "mBluetoothSocket.connect");
            mBluetoothSocket.connect();
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "Erro ao conectar ao socket", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        manageConnectedSocket(mBluetoothSocket);
        mExecuteThread = new ExecuteThread();
        mExecuteThread.start();
    }

    private void manageConnectedSocket(BluetoothSocket socket) {
        Log.i(TAG, "manageConnectedSocket");
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        String msg = "AT E0\r";
        mConnectedThread.write(msg.getBytes());
        msg = "AT L0\r";
        mConnectedThread.write(msg.getBytes());
        msg = "AT ST 00\r";
        mConnectedThread.write(msg.getBytes());
        msg = "AT SP 0\r";
        mConnectedThread.write(msg.getBytes());
        msg = "AT Z\r";
        mConnectedThread.write(msg.getBytes());
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "handleMessage");
            switch (msg.what) {
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    Log.i(TAG, "readMessage:" + readMessage);
                    String[] linhas = readMessage.split("\\r\\n|\\r|\\n", -1);
                    for (int i = 0; i < linhas.length; i++) {
                        String linha = linhas[i];
                        String[] retorno = linha.trim().split(" ");
                        try {
                            if (retorno[0].trim().equals("41")) {
                                if (retorno[1].trim().equals("05")) {
                                    int temp = Integer.parseInt(retorno[2].trim(), 16);
                                    temp = temp - 40;
                                    txtTemperatura.setText(temp + "º C");
                                }
                                if (retorno[1].trim().equals("0C")) {
                                    int p1 = Integer.parseInt(retorno[2].trim(), 16);
                                    int p2 = Integer.parseInt(retorno[3].trim(), 16);
                                    double rpm = (p1 * 256 + p2) / 4;
                                    txtRpm.setText(String.valueOf(rpm));
                                }
                                if (retorno[1].trim().equals("0D")) {
                                    int vel = Integer.parseInt(retorno[2].trim(), 16);
                                    txtVelocidade.setText(vel + " km/h");
                                    speedometer.setSpeed(vel, 1000, 300);
                                }
                                if (retorno[1].trim().equals("11")) {
                                    int bar = Integer.parseInt(retorno[2].trim(), 16);
                                    bar = bar * 100 / 255;
                                    txtPressao.setText(bar + " bar");
                                }
                                if (retorno[1].trim().equals("10")) {
                                    int p1 = Integer.parseInt(retorno[2].trim(), 16);
                                    int p2 = Integer.parseInt(retorno[3].trim(), 16);
                                    double fluxo = (p1 * 256 + p2) / 100;
                                    txtFluxo.setText(fluxo + " m³/min");
                                }
                            }
                            if (retorno[0].trim().equals("05") || retorno[0].trim().equals("0C") || retorno[0].trim().equals("0D") || retorno[0].trim().equals("11") || retorno[0].trim().equals("10")) {
                                Log.i(TAG, "Retorno2: " + retorno[0]);
                                if (retorno[0].trim().equals("05")) {
                                    int temp = Integer.parseInt(retorno[1].trim(), 16);
                                    temp = temp - 40;
                                    txtTemperatura.setText(temp + "º C");
                                }
                                if (retorno[0].trim().equals("0C")) {
                                    int p1 = Integer.parseInt(retorno[1].trim(), 16);
                                    int p2 = Integer.parseInt(retorno[2].trim(), 16);
                                    double rpm = (p1 * 256 + p2) / 4;
                                    txtRpm.setText(String.valueOf(rpm));
                                }
                                if (retorno[0].trim().equals("0D")) {
                                    int vel = Integer.parseInt(retorno[1].trim(), 16);
                                    txtVelocidade.setText(vel + " km/h");
                                    speedometer.setSpeed(vel, 1000, 300);
                                }
                                if (retorno[0].trim().equals("11")) {
                                    int bar = Integer.parseInt(retorno[1].trim(), 16);
                                    bar = bar * 100 / 255;
                                    txtPressao.setText(bar + " bar");
                                }
                                if (retorno[0].trim().equals("10")) {
                                    int p1 = Integer.parseInt(retorno[1].trim(), 16);
                                    int p2 = Integer.parseInt(retorno[2].trim(), 16);
                                    double fluxo = (p1 * 256 + p2) / 100;
                                    txtFluxo.setText(fluxo + " m³/min");
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "erro readMessage:" + readMessage, e);
                        }
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    String writeMessage = new String(writeBuf);
                    break;
            }
        }
    };

    private class ExecuteThread extends Thread {
        public void run() {
            while (true) {
                try {
                    String msg = "";
                    msg = "01 05\r";
                    mConnectedThread.write(msg.getBytes());
                    Thread.sleep(200);
                    msg = "01 0C\r";
                    mConnectedThread.write(msg.getBytes());
                    Thread.sleep(200);
                    msg = "01 0D\r";
                    mConnectedThread.write(msg.getBytes());
                    Thread.sleep(200);
                    msg = "01 11\r";
                    mConnectedThread.write(msg.getBytes());
                    Thread.sleep(200);
                    msg = "01 10\r";
                    mConnectedThread.write(msg.getBytes());

                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = myBluetoothAdapter.listenUsingRfcommWithServiceRecord("obdbt", MY_UUID);
            } catch (IOException e) {
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)
                    manageConnectedSocket(socket);
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        /**
         * Will cancel the listening socket, and cause the thread to finish
         */
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "Exception during read", e);
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            Log.i(TAG, "write");
            try {
                mmOutStream.write(bytes);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, bytes)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }

    public void ligarDesligar(View view){
        if(stLigado == "Ligado")
            desligar(view);
        else
            ligar(view);
    }

    public void ligar(View view){
        Intent turnOnIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(turnOnIntent, REQUEST_ENABLE_BT);
        stLigado = "Ligado";
        txtBluetooth.setText(stLigado);
        btnLigar.setText("Desligar Bluetooth");
        //Toast.makeText(getApplicationContext(), "Bluetooth turned on", Toast.LENGTH_LONG).show();
    }

    public void desligar(View view){
        myBluetoothAdapter.disable();
        stLigado = "Desligado";
        txtBluetooth.setText(stLigado);
        btnLigar.setText("Ligar Bluetooth");
        //Toast.makeText(getApplicationContext(),"Bluetooth turned off", Toast.LENGTH_LONG).show();
    }

    public void localizar(View view) {
        if (myBluetoothAdapter.isDiscovering()) {
            myBluetoothAdapter.cancelDiscovery();
        }else{
            BTArrayAdapter.clear();
            myBluetoothAdapter.startDiscovery();
            registerReceiver(bReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            showdialog();
        }
    }

    public void showdialog(){
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(MainActivity.this);
        builderSingle.setTitle("Selecione um dispositivo:");
        BTArrayAdapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.select_dialog_singlechoice);
        builderSingle.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        builderSingle.setAdapter(BTArrayAdapter, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int pos1, pos2, tam;
                        String strName = BTArrayAdapter.getItem(which);
                        pos1 = strName.indexOf("|") - 1;
                        pos2 = strName.lastIndexOf("|");
                        tam = strName.length();
                        nome = strName.substring(pos1 + 2, pos2);
                        mac = strName.substring(pos2 + 2, tam);
                        situacao = Integer.valueOf(strName.substring(0, pos1));
                        txtNome.setText(nome);
                        txtMac.setText(mac);
                        txtSituacao.setText(textoSituacao(situacao));

                        obdBluetooth();
            }
        });
        builderSingle.create();
        builderSingle.show();
    }

    public String textoSituacao(int codigo){
        String status;
        switch(codigo) {
            case 11:
                status = "Pareando";
                break;
            case 12:
                status = "Pareado";
                break;
            default:
                status = "Desconhecido";
        }
        return status;
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        //unregisterReceiver(bReceiver);
    }
}
