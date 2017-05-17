package speedatacom.mylibrary;

import android.content.Context;
import android.os.Handler;
import android.serialport.DeviceControl;

import java.io.IOException;
import java.util.List;

/**
 * Created by suntianwei on 2017/2/27.
 */

public interface IDistance {
    public enum CmdType {
        Err,
        cmdsingle,
        cmdrepetition,
        cmdstop
    }

    /**
     * 初始串口上电操作  可查看 相关api
     * @param context
     * @param serialPort 串口
     * @param baurate  波特率
     * @param powerType  上电类型
     * @param gpio
     */
    public void initDevice(Context context, String serialPort, int baurate, DeviceControl.PowerType powerType, int... gpio);

    /**
     * 发送指令
     */
    public void senCmd(CmdType cmdType);

    /**
     * 开启读串口线程
     */
    public void startReadThread(Handler handler);

    /**
     * 数据解析
     * @param data  原始数据
     * @param isTop 测量基础
     * @return 解析后的数据
     */
    public List<String> parseData(byte[] data, boolean isTop);

    /**
     * 停止读线程
     */
    public void stopReadThread();


    /**
     * 释放设备
     * @throws IOException
     */
    public void releaseDev() throws IOException;
}
