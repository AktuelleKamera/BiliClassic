package tv.biliclassic;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import android.support.v4.app.Fragment;

public class HomeFragment extends Fragment {

    private LinearLayout partitionsContainer;
    private String[] partitionNames = {"动画", "音乐", "游戏", "娱乐", "电影", "电视剧"};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.content_home, container, false);
        partitionsContainer = (LinearLayout) view.findViewById(R.id.partitions_container);

        for (String name : partitionNames) {
            addPartitionSection(name);
        }
        return view;
    }

    private void addPartitionSection(final String partitionName) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View section = inflater.inflate(R.layout.item_partition_section, partitionsContainer, false);

        // 设置分区名称
        TextView partitionNameView = (TextView) section.findViewById(R.id.partition_name);
        partitionNameView.setText(partitionName);

        // 点击分区按钮
        LinearLayout partitionCard = (LinearLayout) section.findViewById(R.id.partition_card);
        partitionCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getActivity(), "进入" + partitionName + "分区", Toast.LENGTH_SHORT).show();
            }
        });

        // 点击视频封面
        ImageView video1 = (ImageView) section.findViewById(R.id.video1_cover);
        video1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getActivity(), "播放视频", Toast.LENGTH_SHORT).show();
            }
        });

        ImageView video2 = (ImageView) section.findViewById(R.id.video2_cover);
        video2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getActivity(), "播放视频", Toast.LENGTH_SHORT).show();
            }
        });

        partitionsContainer.addView(section);
    }
}