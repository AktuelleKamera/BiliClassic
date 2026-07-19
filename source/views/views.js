enyo.kind({
    name: "myapp.MainView",
    kind: "FittableRows",
    fit: true,
    classes: "theme-pink",
    components: [
        {name: "topBar", kind: "onyx.Toolbar", classes: "top-toolbar", components: [
            {kind: "Image", src: "assets/ic_home.png", style: "height: 40px; width: 93px; vertical-align: middle;"}
        ]},
        {kind: "enyo.Scroller", fit: true, components: [
            {name: "main", classes: "nice-padding", allowHtml: true, content: "欢迎使用哔哩经典！"}
        ]},
        {name: "bottomBar", kind: "onyx.Toolbar", components: [
            {kind: "onyx.Button", content: "手动登录", ontap: "manualLogin"},
            {kind: "onyx.Button", content: "测试av706", ontap: "testPlay"},
            {kind: "onyx.Button", content: "测试网络", ontap: "testNetwork"},
            {kind: "onyx.Button", content: "切换主题", ontap: "toggleTheme"}
        ]},
        {name: "cookiePopup", kind: "onyx.Popup", modal: true, centered: true, floating: true, autoDismiss: false, scrim: true, style: "width: 420px; background: #fff; padding: 16px; text-align: center;", components: [
            {name: "cookieTitle", content: "手动登录", style: "font-size: 18px; margin-bottom: 8px; font-weight: bold; color: #333;"},
            {content: "从浏览器复制 Cookie（包含 DedeUserID 和 bili_jct）粘贴到下面：", style: "font-size: 13px; color: #666; margin: 4px 0;"},
            {name: "cookieInput", kind: "onyx.TextArea", style: "width: 380px; height: 120px; font-size: 12px; font-family: monospace; padding: 6px; margin: 8px 0;"},
            {name: "cookieStatus", content: "", style: "font-size: 14px; color: #666; margin: 4px 0;"},
            {kind: "onyx.Button", content: "保存并登录", ontap: "saveCookies", style: "margin-top: 6px;"},
            {kind: "onyx.Button", content: "取消", ontap: "cancelCookieLogin", style: "margin-top: 6px;"}
        ]}
    ],
    pinkTheme: true,
    toggleTheme: function() {
        this.pinkTheme = !this.pinkTheme;
        if (this.pinkTheme) {
            this.removeClass("theme-black");
            this.addClass("theme-pink");
            this.$.topBar.applyStyle("background", "#D86DA5");
            this.$.topBar.applyStyle("color", "#fff");
            this.$.bottomBar.applyStyle("background", "#D86DA5");
            this.$.bottomBar.applyStyle("color", "#fff");
        } else {
            this.removeClass("theme-pink");
            this.addClass("theme-black");
            this.$.topBar.applyStyle("background", "#333");
            this.$.topBar.applyStyle("color", "#ccc");
            this.$.bottomBar.applyStyle("background", "#333");
            this.$.bottomBar.applyStyle("color", "#ccc");
        }
    },
    testNetwork: function() {
        this.$.main.setContent("正在测试网络连接...<br/>");
        var self = this;
        BiliNet.getJson("/x/web-interface/nav", function(err, data, status) {
            var html = "<b>API 测试结果</b><br/>";
            if (err) {
                html += "错误: " + err.message + "<br/>";
                html += "HTTP状态: " + status + "<br/>";
                self.$.main.setContent(html);
                return;
            }
            html += "HTTP状态: " + status + "<br/>";
            html += "响应码: " + (data ? data.code : "无") + "<br/>";
            if (data && data.data) {
                html += "登录: " + (data.data.isLogin ? "是" : "否") + "<br/>";
                html += "用户: " + (data.data.uname || "未知") + "<br/>";
                html += "MID: " + (data.data.mid || "未知") + "<br/>";
            } else {
                html += "返回: " + JSON.stringify(data).slice(0, 300) + "<br/>";
            }
            self.$.main.setContent(html);
        });
    },
    testVideo: function() {
        this.$.main.setContent("正在获取 av706 信息...<br/>");
        var self = this;
        BiliNet.getJson("/x/web-interface/view?aid=706", function(err, data, status) {
            var html = "<b>av706 视频信息</b><br/>";
            if (err || !data || data.code !== 0) {
                html += "请求失败: " + (err ? err.message : data.message) + "<br/>";
                html += "HTTP: " + status + "<br/>";
                self.$.main.setContent(html);
                return;
            }
            var d = data.data;
            html += "标题: <b>" + (d.title || "无") + "</b><br/>";
            html += "UP: " + (d.owner ? d.owner.name : "无") + "<br/>";
            html += "播放: " + (d.stat ? d.stat.view : "?") + " 次<br/>";
            html += "弹幕: " + (d.stat ? d.stat.danmaku : "?") + "<br/>";
                var dur = d.duration ? Math.floor(d.duration / 60) + ":" + ("0" + (d.duration % 60)).slice(-2) : "?";
            html += "分区: " + (d.tname || "无") + "<br/>";
            html += "描述: " + (d.desc ? (d.desc.length > 100 ? d.desc.slice(0, 100) + "..." : d.desc) : "无") + "<br/>";
            self.$.main.setContent(html);
        });
    },
    manualLogin: function() {
        this.$.cookieInput.setValue("");
        this.$.cookieStatus.setContent("");
        this.$.cookiePopup.show();
    },
    saveCookies: function() {
        var raw = this.$.cookieInput.getValue();
        if (!raw || raw.length < 10) {
            this.$.cookieStatus.setContent("Cookie 内容太短，请完整复制");
            return;
        }
        var self = this;
        CookieStore.clear();
        var pairs = raw.split(";");
        for (var i = 0; i < pairs.length; i++) {
            var p = pairs[i].trim();
            var eq = p.indexOf("=");
            if (eq > 0) {
                var key = p.substring(0, eq).trim();
                var val = p.substring(eq + 1).trim();
                if (key && key !== "undefined") CookieStore.set(key, val);
            }
        }
        self.$.cookieStatus.setContent("Cookie 已保存，验证中...");
        BiliNet.getJson("/x/web-interface/nav", function(err, data) {
            if (!err && data && data.data && data.data.isLogin) {
                var name = data.data.uname || "";
                self.$.cookiePopup.hide();
                self.$.main.setContent("登录成功！用户：<b>" + name + "</b><br/>Cookie 已保存至本地");
            } else {
                self.$.cookieStatus.setContent("登录验证失败，Cookie 可能已过期或格式不正确");
            }
        });
    },
    cancelCookieLogin: function() {
        this.$.cookiePopup.hide();
    },
    testPlay: function() {
        this.$.main.setContent("正在获取 av706 视频信息...<br/>");
        var self = this;
        self._playData = null;
        CookieGen.ensureCookies(function() {
            VideoApi.getVideoInfoByAid(706, function(err, vi) {
                if (err) { self.$.main.setContent("获取失败: " + err.message); return; }
                var h = "<b>" + vi.title + "</b><br/>";
                h += "UP主: " + (vi.staff && vi.staff[0] ? vi.staff[0].name : "?") + "<br/>";
                h += "播放: " + VideoApi.toWan(vi.stats.view || 0) + " | ";
                h += "点赞: " + VideoApi.toWan(vi.stats.like || 0) + "<br/>";
                h += "时长: " + Math.floor(vi.duration / 60) + ":" + (vi.duration % 60 < 10 ? "0" : "") + vi.duration % 60 + "<br/>";
                h += "发布时间: " + vi.timeDesc + "<br/>";
                h += "分P: " + (vi.pagenames ? vi.pagenames.length : 0) + "<br/>";
                h += "简介: " + (vi.description ? vi.description.substring(0, 200) : "无") + "<br/>";
                h += "画质: " + (vi.qualities ? vi.qualities.join(", ") : "?") + "<br/>";
                self._playInfo = vi;
                VideoApi.getPlayUrl(706, vi.cids && vi.cids[0] ? vi.cids[0] : 0, 64, function(err2, urlData) {
                    if (err2) { self.$.main.setContent(h + "<br/>播放地址获取失败: " + err2.message); return; }
                    self._playData = urlData;
                    h += "<br/><b>可用画质:</b> " + (urlData.qnStrList ? urlData.qnStrList.join(" / ") : "?");
                    h += '<br/><br/><button id="playBtn" style="padding:10px 30px;font-size:16px;">▶ 播放视频</button>';
                    self.$.main.setContent(h);
                    var btn = document.getElementById("playBtn");
                    if (btn) btn.onclick = function() { self.playVideo(); };
                });
            });
        });
    },
    playVideo: function() {
        if (!this._playData || !this._playData.videoUrl) return;
        var v = this._playData.videoUrl;
        var self = this;
        var info = this._playInfo;
        var h = "";
        if (info) {
            h += "<b>" + info.title + "</b><br/>";
            h += "UP主: " + (info.staff && info.staff[0] ? info.staff[0].name : "?") + " | ";
            h += "播放: " + VideoApi.toWan(info.stats.view || 0) + " | ";
            h += "点赞: " + VideoApi.toWan(info.stats.like || 0) + "<br/><br/>";
        }
        var proxyUrl = "http://127.0.0.1:18081/proxy/video?url=" + encodeURIComponent(v);
        h += '<video id="biliPlayer" controls autoplay style="width:100%;max-height:400px;" src="' + proxyUrl + '"></video>';
        h += '<div style="text-align:center;margin:8px 0;">';
        h += '<button id="playPauseBtn" style="padding:8px 20px;margin:4px;font-size:16px;">⏸ 暂停</button>';
        h += '<button id="fullscreenBtn" style="padding:8px 20px;margin:4px;font-size:16px;">⛶ 全屏</button>';
        h += '<button id="playBackBtn" style="padding:8px 20px;margin:4px;font-size:16px;">← 返回</button>';
        h += '</div>';
        h += '<br/><a href="' + proxyUrl + '" target="_blank" style="display:inline-block;padding:8px 20px;background:#D86DA5;color:#fff;text-decoration:none;border-radius:4px;">▶ 外部播放</a>';
        this.$.main.setContent(h);
        var video = document.getElementById("biliPlayer");
        var ppBtn = document.getElementById("playPauseBtn");
        var fsBtn = document.getElementById("fullscreenBtn");
        var backBtn = document.getElementById("playBackBtn");
        if (video && ppBtn) {
            ppBtn.onclick = function() {
                if (video.paused) { video.play(); ppBtn.textContent = "⏸ 暂停"; }
                else { video.pause(); ppBtn.textContent = "▶ 播放"; }
            };
            video.onplay = function() { if (ppBtn) ppBtn.textContent = "⏸ 暂停"; };
            video.onpause = function() { if (ppBtn) ppBtn.textContent = "▶ 播放"; };
        }
        if (video && fsBtn) {
            fsBtn.onclick = function() {
                if (video.requestFullscreen) video.requestFullscreen();
                else if (video.webkitRequestFullscreen) video.webkitRequestFullscreen();
                else if (video.mozRequestFullScreen) video.mozRequestFullScreen();
            };
        }
        if (backBtn) backBtn.onclick = function() { self.testPlay(); };
    }
});
