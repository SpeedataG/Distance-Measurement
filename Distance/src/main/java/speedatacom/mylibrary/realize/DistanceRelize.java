package speedatacom.mylibrary.realize;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.serialport.DeviceControl;
import android.serialport.SerialPort;
import android.util.Log;
import android.widget.Toast;

import com.speedata.libutils.DataConversionUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import speedatacom.mylibrary.IDistance;

import static com.speedata.libutils.DataConversionUtils.byteArrayToInt;
import static speedatacom.mylibrary.IDistance.CmdType.Err;

/**
 * Created by suntianwei on 2017/2/27.
 */

public class DistanceRelize implements IDistance {
    private SerialPort mSerialPort;
    private DeviceControl mDeviceControl;
    private int fd;
    private Context mContext;
    private Handler handler;
    private ReadSerialThread serialThread = null;
    private String TAG = "realize";
    private float results = 0;
    //发送命令 单次测距：    AT1#
    private static byte[] cmd_single = new byte[]{0x41, 0x54, 0x31, 0x23};

    //控制机发：连续测量    AT3#
    private static byte[] cmd_repetition = new byte[]{0x41, 0x54, 0x33, 0x23};

    // 停止测距 ATX#
    private static byte[] cmd_stop = new byte[]{0x41, 0x54, 0x58, 0x23};

    private static byte[] send = new byte[]{0x41, 0x54, 0x47, 0x23};//ATG# 初始化设备
    private CmdType mcmdType=Err;
    @Override
    public void initDevice(Context context,String serialPort, int baurate, DeviceControl.PowerType powerType, int... gpio) {
        this.mContext=context;
        try {
            mSerialPort = new SerialPort();
            mSerialPort.OpenSerial(serialPort, baurate);
            fd = mSerialPort.getFd();
            mDeviceControl = new DeviceControl(powerType, gpio);
            mDeviceControl.PowerOnDevice();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void senCmd(CmdType cmdType) {
        this.mcmdType = cmdType;
        switch (mcmdType) {
            case cmdsingle:
                mSerialPort.WriteSerialByte(fd, cmd_single);
                break;
            case cmdrepetition:
                mSerialPort.WriteSerialByte(fd, cmd_repetition);
                break;
            case cmdstop:
                mSerialPort.WriteSerialByte(fd, cmd_stop);
                break;
        }
    }

    @Override
    public void startReadThread(Handler handler) {
        this.handler = handler;
        if (serialThread == null) {
            serialThread = new ReadSerialThread();
            serialThread.start();
        }
    }

    @Override
    public List<String> parseData(byte[] data, boolean isTop) {
        this.isTop=isTop;
        return parseData(data);
    }

    @Override
    public void stopReadThread() {
        if (serialThread != null) {
            serialThread.interrupt();
            serialThread = null;
        }
    }

    @Override
    public void releaseDev() throws IOException {
        mcmdType=Err;
        mSerialPort.CloseSerial(fd);
        mDeviceControl.PowerOffDevice();
    }

    /**
     * 读串口线程
     */
    private class ReadSerialThread extends Thread {
        @Override
        public void run() {
            super.run();
            while (!isInterrupted()) {
                try {
                    SystemClock.sleep(100);
                    byte[] bytes = mSerialPort.ReadSerial(mSerialPort.getFd(), 1024);
                    if (bytes != null) {
                        String log = "";
                        for (byte x : bytes) {
                            log += String.format("0x%x", x);
                        }
                        Log.d(TAG, "Read_length=" + log);
                        Message msg = new Message();
                        msg.obj = bytes;
                        handler.sendMessage(msg);
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    boolean isTop = true;

    /**
     * 计算长度
     *
     * @param data
     * @return
     */
    private String convertValue(byte[] data) {

        int number = byteArrayToInt(data);
        Log.e(TAG, "convertValue: " + number);
        if (isTop) {
            results = (float) ((number - 700) / 10000.0);//从顶部开始计算
        } else {
            results = (float) ((number + 1000) / 10000.0);//从底部开始计算
        }
        //四舍五入
        DecimalFormat df = new DecimalFormat("#,##0.00");
        String dff = df.format(results);
        return dff;
    }

    byte SingleStrat[] = {65, 84, 49, 35};//AT1#
    byte AutoStrat[] = {65, 84, 51, 35};//AT3#
    byte Stop[] = {65, 84, 88, 35};//atx#
    byte err[] = {65, 84, 69, 49, 35};//ATE1# 机器发生错误信号弱
    byte err2[] = {65, 84, 69, 56, 35};//ATE8# 超出测量范围
    byte InitDevice[] = {65, 84, 71, 35};//ATG#  仪器上电复位后，请上位机收到ATG# 后，才能认为数据有效
    private boolean isInitDevice = true;


    /**
     * 解析串口原始数据
     *
     * @param data
     * @return
     */
    public List<String> parseData(byte[] data) {
        List<String> result = new ArrayList<>();

        if (data.length < 8) {
            Log.e(TAG, "====parseData len error" + DataConversionUtils.byteArrayToStringLog(data,
                    data.length));
            if (Arrays.equals(data, err) || Arrays.equals(data, err2)) {
//                cmdType=Err;
                Toast.makeText(mContext, "超出测量范围或信号弱！", Toast.LENGTH_SHORT).show();
                return  null;
            }
            if (isInitDevice) {
                if (Arrays.equals(data, InitDevice)) {
                    isInitDevice = false;
                } else {
                    try {
                        mDeviceControl.PowerOffDevice();
                        SystemClock.sleep(100);
                        mDeviceControl.PowerOnDevice();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            switch (mcmdType) {
                case cmdsingle:
                    if (!Arrays.equals(data, SingleStrat)) {
                        Log.d(TAG, "SingleStrat=");
                        mSerialPort.clearPortBuf(fd);
                        mSerialPort.WriteSerialByte(fd, cmd_single);
                    }
                    break;
                case cmdrepetition:
                    if (!Arrays.equals(data, AutoStrat)) {
                        Log.d(TAG, "AutoStrat=");
                        mSerialPort.clearPortBuf(fd);
                        mSerialPort.WriteSerialByte(fd, cmd_repetition);
                    }
                    break;
                case cmdstop:
                    if (!Arrays.equals(data, Stop)) {
                        Log.d(TAG, "Stop=");
                        mSerialPort.WriteSerialByte(fd, cmd_stop);
                    }
                    break;
                default:
                    return result;

            }
            return result;
        }
        for (int i = 0; i < data.length; i++) {
            try {
                if ((byte) data[i] == 0x041 && (byte) data[i + 7] == 0x023) {
                    byte[] temp = new byte[8];
                    System.arraycopy(data, i, temp, 0, 8);
                    //加法和
                    byte sum = (byte) (temp[3] + temp[4] + temp[5]);
                    byte[] values = new byte[3];
                    System.arraycopy(temp, 3, values, 0, 3);
                    //判断校验
                    if (sum == temp[6]) {
                        String object = convertValue(values);
                        result.add(object);
                        Log.d(TAG, "====parseData add" + object);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;

    }
}
