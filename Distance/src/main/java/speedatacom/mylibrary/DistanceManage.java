package speedatacom.mylibrary;


import speedatacom.mylibrary.realize.DistanceRelize;

/**
 * Created by suntianwei on 2017/2/28.
 */

public class DistanceManage {
    public static IDistance iDistance;

    public static IDistance getDistanceIntance() {
        if (iDistance == null) {
            iDistance = new DistanceRelize() ;
        }
        return iDistance;
    }

}
