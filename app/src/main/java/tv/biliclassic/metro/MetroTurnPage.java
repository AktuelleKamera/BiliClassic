package tv.biliclassic.metro;

/**
 * 组成 MetroHome "详情页"的内容页统一接口：
 * 转门翻入/翻出时，由宿主调用各自的内容错峰动画（磁贴/大字项）。
 */
public interface MetroTurnPage {
    void animateTurnIn();
    void animateTurnOut();
}
