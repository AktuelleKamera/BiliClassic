package tv.biliclassic.tv;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import tv.biliclassic.HistoryActivity;
import tv.biliclassic.MainActivity;
import tv.biliclassic.R;
import tv.biliclassic.SearchActivity;
import tv.biliclassic.SettingsActivity;
import tv.biliclassic.tv.adapter.TvGridAdapter;

public class TvHomeFragment extends Fragment {

    private GridView gridView;
    private TvGridAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tv_home, container, false);

        gridView = (GridView) view.findViewById(R.id.tv_grid);
        adapter = new TvGridAdapter(getActivity());
        gridView.setAdapter(adapter);

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String label = adapter.getItem(position);
                handleTileClick(label);
            }
        });

        // 等布局完成后，让第一个 item 获得焦点
        gridView.post(new Runnable() {
            @Override
            public void run() {
                if (gridView.getChildCount() > 0) {
                    View firstChild = gridView.getChildAt(0);
                    if (firstChild != null) {
                        firstChild.requestFocus();
                    }
                }
            }
        });

        return view;
    }

    private void handleTileClick(String label) {
        if (label == null) return;

        if (label.equals("追番")) {
            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.putExtra("tab_index", 2);
            startActivity(intent);
        } else if (label.equals("推荐")) {
            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.putExtra("tab_index", 4);
            startActivity(intent);
        } else if (label.equals("时间线")) {
            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.putExtra("tab_index", 3);
            startActivity(intent);
        } else if (label.equals("分区")) {
            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.putExtra("tab_index", 1);
            startActivity(intent);
        } else if (label.equals("搜索")) {
            startActivity(new Intent(getActivity(), SearchActivity.class));
        } else if (label.equals("历史")) {
            startActivity(new Intent(getActivity(), HistoryActivity.class));
        } else if (label.equals("设置")) {
            startActivity(new Intent(getActivity(), SettingsActivity.class));
        }
    }
}