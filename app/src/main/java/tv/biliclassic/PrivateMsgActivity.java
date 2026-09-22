package tv.biliclassic;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import tv.biliclassic.api.PrivateMsgApi;
import tv.biliclassic.model.PrivateMessage;
import tv.biliclassic.util.MsgUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * 私信聊天页
 */
public class PrivateMsgActivity extends BaseActivity {

    private static final long REFRESH_INTERVAL = 15000L;

    private ListView msgList;
    private EditText input;
    private Button sendBtn;
    private PrivateMsgAdapter adapter;
    private List<PrivateMessage> messages = new ArrayList<PrivateMessage>();

    private long uid;
    private String talkerName;
    private long myUid;
    private String myName;

    private boolean isLoading = false;
    private Handler refreshHandler = new Handler();
    private Runnable refreshRunnable;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_msg);
        initRoundTitleBar();

        Intent intent = getIntent();
        uid = intent.getLongExtra("uid", 0);
        talkerName = intent.getStringExtra("name");

        msgList = (ListView) findViewById(R.id.msg_list);
        input = (EditText) findViewById(R.id.msg_input);
        sendBtn = (Button) findViewById(R.id.send_btn);
        TextView title = (TextView) findViewById(R.id.title_text);
        if (talkerName != null && talkerName.length() > 0) {
            title.setText(talkerName);
        }

        msgList.setCacheColorHint(0x00000000);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        myUid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        myName = SharedPreferencesUtil.getString("uname", "我");
        adapter = new PrivateMsgAdapter(this, messages, myUid);
        msgList.setAdapter(adapter);

        if (uid == 0) {
            MsgUtil.showMsg(this, "无法打开该会话");
            finish();
            return;
        }
        if (myUid == 0) {
            MsgUtil.showMsg(this, "请先登录");
            finish();
            return;
        }

        loadMessages(true);
        scheduleRefresh();
    }

    // 定时拉取新消息
    private void scheduleRefresh() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (refreshHandler == null) return;
                loadMessages(false);
                refreshHandler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    private void loadMessages(final boolean scrollBottom) {
        if (isLoading) return;
        isLoading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject data = PrivateMsgApi.getPrivateMsg(uid, 50, 0, 0);
                    final ArrayList<PrivateMessage> list = PrivateMsgApi.getPrivateMsgList(
                            data, myUid, myName, uid, talkerName);
                    Collections.reverse(list);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            boolean changed = list.size() != messages.size();
                            if (!changed && list.size() > 0) {
                                changed = list.get(list.size() - 1).msgSeqno != messages.get(messages.size() - 1).msgSeqno;
                            }
                            if (changed) {
                                messages.clear();
                                messages.addAll(list);
                                adapter.notifyDataSetChanged();
                                if (scrollBottom && messages.size() > 0) {
                                    msgList.setSelection(messages.size() - 1);
                                }
                            }
                        }
                    });
                    try {
                        PrivateMsgApi.updateAck(uid, 1, 0);
                    } catch (Exception ignore) {
                    }
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                        }
                    });
                }
            }
        }).start();
    }

    private void sendMessage() {
        final String content = input.getText().toString().trim();
        if (content.length() == 0) {
            MsgUtil.showMsg(this, "还没有输入内容");
            return;
        }
        sendBtn.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String safe = content.replace("\\", "\\\\").replace("\"", "\\\"");
                    final JSONObject result = PrivateMsgApi.sendMsg(myUid, uid,
                            PrivateMessage.TYPE_TEXT, System.currentTimeMillis() / 1000,
                            "{\"content\":\"" + safe + "\"}");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            sendBtn.setEnabled(true);
                            if (result.optInt("code", -1) == 0) {
                                input.setText("");
                                loadMessages(true);
                            } else {
                                MsgUtil.showMsg(PrivateMsgActivity.this,
                                        "发送失败：" + result.optString("message", "未知错误"));
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            sendBtn.setEnabled(true);
                            MsgUtil.showMsg(PrivateMsgActivity.this, "发送失败");
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        if (refreshHandler != null) {
            if (refreshRunnable != null) {
                refreshHandler.removeCallbacks(refreshRunnable);
            }
            refreshHandler = null;
        }
        super.onDestroy();
    }
}
