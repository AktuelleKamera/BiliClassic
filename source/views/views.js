enyo.kind({
    name: "myapp.MainView",
    kind: "FittableRows",
    fit: true,
    classes: "theme-pink",
    components: [
        {name: "topBar", kind: "onyx.Toolbar", classes: "top-toolbar", components: [
            {kind: "Image", src: "assets/ic_home.png", style: "height: 40px; width: 93px; vertical-align: middle;"}
        ]},
        {name: "searchBar", kind: "onyx.Toolbar", classes: "search-bar", style: "padding:4px;", components: [
            {name: "searchInput", kind: "onyx.Input", style: "width:70%;height:32px;font-size:14px;", placeholder: "输入关键词 / AV号 / BV号"},
            {kind: "onyx.Button", content: "搜索", ontap: "doSearch", style: "margin-left:4px;"}
        ]},
        {kind: "enyo.Scroller", fit: true, components: [
            {name: "main", classes: "nice-padding", allowHtml: true, content: "正在加载推荐..."}
        ]},
        {name: "bottomBar", kind: "onyx.Toolbar", components: [
            {kind: "onyx.Button", content: "推荐", ontap: "showTab", style: "margin:2px;", tabId: "recommend"},
            {kind: "onyx.Button", content: "历史", ontap: "showTab", style: "margin:2px;", tabId: "history"},
            {kind: "onyx.Button", content: "登录", ontap: "manualLogin", style: "margin:2px;"},
            {kind: "onyx.Button", content: "主题", ontap: "toggleTheme", style: "margin:2px;"},
            {kind: "onyx.Button", content: "关于", ontap: "showAbout", style: "margin:2px;"}
        ]},
        {name: "aboutPopup", kind: "onyx.Popup", modal: true, centered: true, floating: true, autoDismiss: false, scrim: true, style: "width: 480px; background: #fff; padding: 16px; text-align: center; max-height: 90%; overflow-y: auto;", components: [
            {name: "aboutContent", allowHtml: true, style: "font-size: 14px; color: #333; text-align: left;"},
            {kind: "onyx.Button", content: "关闭", ontap: "closeAbout", style: "margin-top: 12px;"}
        ]},
        {name: "cookiePopup", kind: "onyx.Popup", modal: true, centered: true, floating: true, autoDismiss: false, scrim: true, style: "width: 420px; background: #fff; padding: 12px; text-align: center; max-height: 90%; overflow-y: auto;", components: [
            {name: "cookieTitle", content: "登录", style: "font-size: 18px; margin-bottom: 8px; font-weight: bold; color: #333;"},
            {content: "从哔哩终端复制登录信息粘贴到下面：", style: "font-size: 13px; color: #666; margin: 4px 0;"},
            {name: "cookieInput", kind: "onyx.TextArea", style: "width: 380px; height: 60px; font-size: 12px; font-family: monospace; padding: 6px; margin: 6px 0;"},
            {name: "cookieStatus", content: "", style: "font-size: 13px; color: #666; margin: 4px 0;"},
            {kind: "onyx.Button", content: "保存并登录", ontap: "saveCookies", style: "margin: 4px;"},
            {kind: "onyx.Button", content: "取消", ontap: "cancelCookieLogin", style: "margin: 4px;"}
        ]},
        {name: "videoPopup", kind: "onyx.Popup", modal: true, centered: true, floating: true, autoDismiss: false, scrim: true, style: "width: 480px; background: #fff; padding: 16px; text-align: center; max-height: 90%; overflow-y: auto;", components: [
            {name: "videoTitle", content: "", style: "font-size: 18px; font-weight: bold; color: #333; margin-bottom: 8px;"},
            {name: "videoCover", allowHtml: true, style: "margin: 8px 0;"},
            {name: "videoInfo", allowHtml: true, style: "font-size: 14px; color: #555; margin: 8px 0; text-align: left;"},
            {name: "videoActions", style: "margin-top: 12px;", components: [
                {kind: "onyx.Button", content: "▶ 播放", ontap: "playCurrentVideo", style: "margin: 4px;"},
                {kind: "onyx.Button", content: "关闭", ontap: "closeVideoPopup", style: "margin: 4px;"}
            ]}
        ]}
    ],
    pinkTheme: true,
    _currentTab: "recommend",
    create: function() {
        this.inherited(arguments);
        var self = this;
        setTimeout(function() { self.loadRecommend(); }, 100);
    },
    showTab: function(inSender) {
        this._currentTab = inSender.tabId;
        if (inSender.tabId === "recommend") this.loadRecommend();
        else if (inSender.tabId === "history") this.loadHistory();
    },
    toggleTheme: function() {
        this.pinkTheme = !this.pinkTheme;
        if (this.pinkTheme) {
            this.removeClass("theme-black"); this.addClass("theme-pink");
            this.$.topBar.applyStyle("background", "#D86DA5");
            this.$.bottomBar.applyStyle("background", "#D86DA5");
        } else {
            this.removeClass("theme-pink"); this.addClass("theme-black");
            this.$.topBar.applyStyle("background", "#333");
            this.$.bottomBar.applyStyle("background", "#333");
        }
    },
    loadRecommend: function(page) {
        var self = this; page = page || 1;
        if (page === 1) { this.$.main.setContent("正在加载推荐..."); this._recPage = 1; this._recLoading = false; this._recContent = ""; this._recSeen = {}; }
        if (this._recLoading) return;
        this._recLoading = true;
        RecommendApi.getPopular(page, function(err, list) {
            self._recLoading = false;
            if (err || !list || list.length === 0) {
                if (page === 1) self.$.main.setContent("获取失败无数据<br/><button onclick='app.view.loadRecommend()'>重试</button>");
                return;
            }
            var cards = ""; var added = 0;
            for (var i = 0; i < list.length && added < 6; i++) {
                var v = list[i];
                if (self._recSeen[v.aid]) continue;
                self._recSeen[v.aid] = true; added++;
                var thumb = v.cover.indexOf('?') > 0 ? v.cover.split('?')[0] : v.cover;
                thumb += '@240w_135h_1c.jpg';
                cards += '<div class="rec-card" onclick="app.view.showVideoInfo(' + v.aid + ')">';
                cards += '<div class="rec-cover-wrap"><div style="background:#e0e0e0;width:100%;height:0;padding-top:56.25%;position:relative;">';
                cards += '<img src="' + thumb + '" referrerpolicy="no-referrer" style="position:absolute;top:0;left:0;width:100%;height:100%;object-fit:cover;" onerror="this.style.display=\'none\'" loading="lazy"/>';
                cards += '</div><div class="rec-overlay">';
                cards += '<img src="assets/ic_info_views.png" style="height:12px;vertical-align:middle;" onerror="this.style.display=\'none\'"/> <span style="font-size:11px;color:#fff;">' + v.view + '</span>';
                cards += '<img src="assets/ic_info_danmakus.png" style="height:12px;margin-left:6px;vertical-align:middle;" onerror="this.style.display=\'none\'"/> <span style="font-size:11px;color:#fff;">' + v.danmaku + '</span>';
                cards += '</div></div><div class="rec-title">' + v.title + '</div><div class="rec-up">' + v.upName + '</div></div>';
            }
            self._recContent = self._recContent + cards;
            var total = Object.keys(self._recSeen).length;
            if (total > 48) {
                var aids = Object.keys(self._recSeen); var keep = aids.slice(aids.length - 48);
                self._recSeen = {}; for (var k = 0; k < keep.length; k++) self._recSeen[keep[k]] = true;
                self._recContent = cards;
            }
            self.$.main.setContent('<div class="rec-grid">' + self._recContent + '<div style="clear:both;"></div></div>');
            self._recPage = page;
            self._bindInfiniteScroll();
        });
    },
    _bindInfiniteScroll: function() {
        var self = this; var scroller = this.$.main.parent;
        if (!scroller || scroller._scrollBound) return;
        scroller._scrollBound = true;
        scroller.hasNode().addEventListener("scroll", function() {
            if (self._currentTab !== "recommend") return;
            var el = self.$.main.hasNode(); if (!el || self._recLoading) return;
            var scrollEl = scroller.hasNode();
            if (scrollEl.scrollTop + scrollEl.clientHeight >= scrollEl.scrollHeight - 200) {
                self.loadRecommend((self._recPage || 1) + 1);
            }
        });
    },
    loadHistory: function() {
        var self = this;
        this._histCursor = { viewAt: 0, business: '', offset: 0 };
        this._histItems = [];
        this._histLoading = false;
        this._histBottom = false;
        this._appendHistory();
    },
    _appendHistory: function() {
        var self = this;
        if (this._histLoading || this._histBottom) return;
        this._histLoading = true;
        if (this._histItems.length === 0) self.$.main.setContent("加载中...");
        HistoryApi.getHistory(this._histCursor, function(err, result) {
            self._histLoading = false;
            if (err || !result || !result.items) {
                if (self._histItems.length === 0) self.$.main.setContent("加载失败<br/><button onclick='app.view.loadHistory()'>重试</button>");
                return;
            }
            if (result.items.length === 0 && self._histItems.length === 0) {
                self.$.main.setContent("暂无历史记录<br/>需要先登录才能查看");
                return;
            }
            for (var i = 0; i < result.items.length; i++) self._histItems.push(result.items[i]);
            self._histCursor = result.cursor;
            self._histBottom = result.isBottom;
            var h = '<table style="width:100%;border-collapse:collapse;font-size:13px;">';
            h += '<tr style="background:#D86DA5;color:#fff;"><th style="padding:6px;">标题</th><th style="padding:6px;">UP主</th><th style="padding:6px;">进度</th></tr>';
            for (var i = 0; i < self._histItems.length; i++) {
                var v = self._histItems[i];
                h += '<tr style="border-bottom:1px solid #eee;cursor:pointer;" onclick="app.view.showVideoInfo(' + v.aid + ')">';
                h += '<td style="padding:6px;">' + v.title + '</td>';
                h += '<td style="padding:6px;">' + v.upName + '</td>';
                h += '<td style="padding:6px;">' + v.view + '</td></tr>';
            }
            h += '</table>';
            self.$.main.setContent(h);
            self._bindHistoryScroll();
        });
    },
    _bindHistoryScroll: function() {
        var self = this;
        var scroller = this.$.main.parent;
        if (!scroller || scroller._histScrollBound) return;
        scroller._histScrollBound = true;
        scroller.hasNode().addEventListener("scroll", function() {
            if (self._currentTab !== "history") return;
            var scrollEl = scroller.hasNode();
            if (scrollEl.scrollTop + scrollEl.clientHeight >= scrollEl.scrollHeight - 200) {
                self._appendHistory();
            }
        });
    },
    showVideoInfo: function(aid) {
        var self = this;
        // 断开滚动监听，防止历史/推荐覆盖
        var scroller = this.$.main.parent;
        if (scroller) {
            scroller._scrollBound = false;
            scroller._histScrollBound = false;
        }
        this.$.main.setContent("加载中...");
        VideoApi.getVideoInfoByAid(aid, function(err, vi) {
            if (err) { self.$.main.setContent("获取失败: " + err.message); return; }
            self._playInfo = vi;
            self._playAid = aid;
            var h = '';
            h += '<div style="background:#000;text-align:center;max-height:220px;overflow:hidden;"><img src="' + (vi.cover || '') + '" style="max-width:100%;max-height:220px;" referrerpolicy="no-referrer" onerror="this.style.display=\'none\'"/></div>';
            h += '<div style="padding:8px;"><b style="font-size:16px;">' + vi.title + '</b></div>';
            h += '<div style="padding:0 8px 8px;font-size:13px;color:#888;">';
            h += 'UP主: ' + (vi.staff && vi.staff[0] ? vi.staff[0].name : '?') + ' | ';
            h += '播放: ' + VideoApi.toWan(vi.stats.view || 0) + ' | ';
            h += '点赞: ' + VideoApi.toWan(vi.stats.like || 0) + ' | ';
            h += '弹幕: ' + VideoApi.toWan(vi.stats.danmaku || 0) + '<br/>';
            var dur = Math.floor((vi.duration || 0) / 60) + ':' + ((vi.duration || 0) % 60 < 10 ? '0' : '') + (vi.duration || 0) % 60;
            h += '时长: ' + dur + ' | 分P: ' + (vi.pagenames ? vi.pagenames.length : 0);
            h += '</div>';
            if (vi.description) h += '<div style="padding:0 8px 8px;font-size:12px;color:#999;border-top:1px solid #eee;padding-top:8px;">' + vi.description.slice(0, 200) + (vi.description.length > 200 ? '...' : '') + '</div>';
            h += '<div style="padding:12px;text-align:center;">';
            h += '<button onclick="app.view.playVideoByAid(' + aid + ')" style="padding:10px 40px;background:#D86DA5;color:#fff;font-size:16px;border:none;border-radius:4px;cursor:pointer;">▶ 播放</button>';
            h += '</div>';
            self.$.main.setContent(h);
        });
    },
    playVideoByAid: function(aid) {
        var self = this;
        var scroller = this.$.main.parent;
        if (scroller) { scroller._scrollBound = false; scroller._histScrollBound = false; }
        VideoApi.getPlayUrl(aid, this._playInfo && this._playInfo.cids && this._playInfo.cids[0] ? this._playInfo.cids[0] : 0, 64, function(err, urlData) {
            if (err || !urlData || !urlData.videoUrl) {
                self.$.main.setContent("播放地址获取失败" + (err ? ": " + err.message : ""));
                return;
            }
            window.open(urlData.videoUrl, "_blank");
        });
    },
    doSearch: function() {
        var self = this;
        var input = this.$.searchInput.getValue().trim();
        if (!input) return;
        // 断开滚动监听，防止推荐覆盖
        var scroller = this.$.main.parent;
        if (scroller) { scroller._scrollBound = false; scroller._histScrollBound = false; }
        var resolved = SearchApi.resolveInput(input);
        if (resolved.type === "av") {
            this.showVideoInfo(resolved.id);
            return;
        }
        if (resolved.type === "bv") {
        }
        this._searchKeyword = resolved.keyword;
        this._searchPage = 1;
        this._searchResults = [];
        this._doSearchPage();
    },
    _doSearchPage: function() {
        var self = this;
        if (this._searchPage === 1) this.$.main.setContent("搜索中...");
        SearchApi.search(this._searchKeyword, this._searchPage, function(err, results) {
            if (err) { self.$.main.setContent("搜索失败: " + err.message); return; }
            if (!results || results.length === 0) {
                if (self._searchPage === 1) self.$.main.setContent("没有找到结果");
                return;
            }
            for (var i = 0; i < results.length; i++) self._searchResults.push(results[i]);
            var h = '<div style="padding:4px;">';
            for (var i = 0; i < self._searchResults.length; i++) {
                var r = self._searchResults[i];
                h += '<div class="rec-card" onclick="app.view.showVideoInfo(' + r.aid + ')">';
                h += '<div class="rec-cover-wrap"><div style="background:#e0e0e0;width:100%;height:0;padding-top:56.25%;position:relative;">';
                h += '<img src="' + r.cover + '@240w_135h_1c.jpg" referrerpolicy="no-referrer" style="position:absolute;top:0;left:0;width:100%;height:100%;object-fit:cover;" onerror="this.style.display=\'none\'" loading="lazy"/>';
                h += '</div></div><div class="rec-title">' + r.title + '</div><div class="rec-up">' + r.upName + '</div></div>';
            }
            h += '</div>';
            h += '<div style="text-align:center;padding:8px;">';
            if (self._searchPage > 1) {
                h += '<button onclick="app.view._searchPrevPage()" style="padding:6px 16px;margin:4px;font-size:14px;">上一页</button>';
            }
            h += '<button onclick="app.view._searchNextPage()" style="padding:6px 16px;margin:4px;font-size:14px;">下一页</button>';
            h += '<span style="font-size:13px;color:#888;margin:4px;">第 ' + self._searchPage + ' 页</span>';
            h += '</div>';
            self.$.main.setContent(h);
        });
    },
    _searchNextPage: function() {
        this._searchPage++;
        this._doSearchPage();
    },
    _searchPrevPage: function() {
        if (this._searchPage > 1) this._searchPage--;
        this._doSearchPage();
    },
    testNetwork: function() {
        this.$.main.setContent("正在测试...<br/>");
        var self = this;
        BiliNet.getJson("/x/web-interface/nav", function(err, data, status) {
            var html = "<b>API 测试结果</b><br/>";
            if (err) { html += "错误: " + err.message + "<br/>HTTP状态: " + status; self.$.main.setContent(html); return; }
            html += "HTTP状态: " + status + "<br/>响应码: " + (data ? data.code : "无") + "<br/>";
            if (data && data.data) { html += "登录: " + (data.data.isLogin ? "是" : "否") + "<br/>用户: " + (data.data.uname || "未知"); }
            self.$.main.setContent(html);
        });
    },
    manualLogin: function() {
        this.$.cookieInput.setValue(""); this.$.cookieStatus.setContent(""); this.$.cookiePopup.show();
    },
    saveCookies: function() {
        var raw = this.$.cookieInput.getValue();
        if (!raw || raw.length < 10) { this.$.cookieStatus.setContent("内容太短，请完整复制"); return; }
        var self = this;
        function extractValue(text, key) {
            var patterns = [key + '=([^;\\s\\n\\r\"]+?)(?:;|\\s|$)', key + '=\"([^\"]+?)\"', '"' + key + '"\\s*:\\s*\"([^\"]+?)\"'];
            for (var p = 0; p < patterns.length; p++) { var re = new RegExp(patterns[p], 'i'); var m = re.exec(text); if (m) return m[1].replace(/[;\s]+$/, '').trim(); }
            return null;
        }
        var cookies = null;
        try { var j = JSON.parse(raw); if (j && j.cookies) cookies = j.cookies; } catch(e) {}
        if (!cookies) {
            var sess = extractValue(raw, 'SESSDATA');
            if (sess) {
                var parts = [];
                var fields = {SESSDATA: sess, bili_jct: extractValue(raw, 'bili_jct'), DedeUserID: extractValue(raw, 'DedeUserID'), buvid3: extractValue(raw, 'buvid3'), buvid4: extractValue(raw, 'buvid4'), sid: extractValue(raw, 'sid')};
                for (var k in fields) { if (fields[k]) parts.push(k + '=' + fields[k]); }
                cookies = parts.join('; ');
            } else { cookies = raw; }
        }
        if (!cookies || cookies.indexOf('SESSDATA') < 0) { self.$.cookieStatus.setContent("未找到 SESSDATA，请从哔哩终端复制"); return; }
        CookieStore.clear();
        var pairs = cookies.split("; ");
        for (var i = 0; i < pairs.length; i++) {
            var p = pairs[i].trim(); var eq = p.indexOf("=");
            if (eq > 0) { var key = p.substring(0, eq).trim(); var val = p.substring(eq + 1).trim(); if (key && key !== "undefined") CookieStore.set(key, val); }
        }
        self.$.cookieStatus.setContent("Cookie 已保存，验证中...");
        BiliNet.getJson("/x/web-interface/nav", function(err, data) {
            if (!err && data && data.data && data.data.isLogin) {
                var name = data.data.uname || "";
                self.$.cookiePopup.hide();
                self.$.main.setContent("登录成功！用户：<b>" + name + "</b><br/>Cookie 已保存");
            } else { self.$.cookieStatus.setContent("验证失败，Cookie 可能已过期"); }
        });
    },
    cancelCookieLogin: function() { this.$.cookiePopup.hide(); },
    showAbout: function() {
        var self = this;
        var h = '<div style="text-align:center;margin-bottom:12px;"><b style="font-size:18px;">BiliClassic for webOS</b></div>';
        h += '<div style="text-align:center;font-size:13px;color:#888;margin-bottom:12px;">版本 0.2.0</div>';
        h += '<div style="text-align:center;margin:8px 0;"><button onclick="app.view.checkUpdate()" style="padding:8px 20px;background:#D86DA5;color:#fff;border:none;border-radius:4px;font-size:14px;cursor:pointer;">检查更新</button></div>';
        h += '<div id="updateResult" style="font-size:13px;color:#666;margin:8px 0;text-align:center;"></div>';
        h += '<hr style="border:none;border-top:1px solid #eee;margin:12px 0;"/>';
        h += '<div style="text-align:center;margin:8px 0;">';
        h += '<a href="http://www.biliclassic.cn" target="_blank" style="display:inline-block;padding:8px 24px;background:#D86DA5;color:#fff;text-decoration:none;border-radius:4px;font-size:14px;">访问 www.biliclassic.cn</a>';
        h += '</div>';
        h += '<div style="font-size:12px;color:#999;margin-top:8px;text-align:center;">基于 Enyo 2.5.1 | HP TouchPad webOS 3.0</div>';
        self.$.aboutContent.setContent(h);
        self.$.aboutPopup.show();
    },
    closeAbout: function() { this.$.aboutPopup.hide(); },
    checkUpdate: function() {
        var el = document.getElementById("updateResult");
        if (el) el.innerHTML = "检查中...";
        BiliNet.get("http://www.biliclassic.cn/webos/api/version.json?t=" + Date.now(), function(err, resp) {
            var txt = "";
            if (!el) return;
            if (err || !resp || resp.status !== 200) {
                el.innerHTML = "检查失败，请检查网络连接";
                return;
            }
            try {
                var data = JSON.parse(resp.text);
                var current = 200;
                var latest = data.version_code || 0;
                if (latest > current) {
                    txt = '发现新版本 <b>' + data.version + '</b>！<br/>';
                    if (data.download_url) txt += '<a href="' + data.download_url + '" target="_blank" style="display:inline-block;padding:6px 16px;background:#D86DA5;color:#fff;text-decoration:none;border-radius:4px;margin-top:6px;">下载更新</a>';
                } else {
                    txt = "当前已是最新版本 " + data.version;
                }
                if (data.changelog && data.changelog.length > 0) {
                    txt += '<br/><div style="text-align:left;margin-top:8px;font-size:12px;color:#888;">' + data.changelog.join('<br/>') + '</div>';
                }
            } catch(e) {
                txt = "解析失败";
            }
            el.innerHTML = txt;
        });
    },
    showMain: function() { this.$.searchBar.show(); this.loadRecommend(); },
    showAbout: function() {
        var self = this;
        this.$.main.setContent("正在检查...");
        self._checkUpdate(function(hasUpdate, remoteVer, info) {
            var h = '<div style="padding:12px;">';
            h += '<b style="font-size:18px;">哔哩经典 for webOS</b><br/>';
            h += '<span style="font-size:13px;color:#888;">当前版本: 0.2.0</span><br/>';
            h += '<span style="font-size:13px;color:#888;">官网: <a href="http://www.biliclassic.cn" target="_blank" style="color:#D86DA5;">www.biliclassic.cn</a></span><br/><br/>';
            if (hasUpdate) {
                h += '<span style="color:#D86DA5;font-weight:bold;">发现新版本: ' + remoteVer + '</span><br/>';
                if (info && info.changelog) {
                    h += '<div style="font-size:12px;color:#555;margin:8px 0;background:#f9f9f9;padding:8px;border-radius:4px;">';
                    for (var i = 0; i < info.changelog.length; i++) {
                        h += info.changelog[i] + '<br/>';
                    }
                    h += '</div>';
                }
                if (info && info.download_url) {
                    h += '<a href="' + info.download_url + '" target="_blank" style="display:inline-block;padding:8px 20px;background:#D86DA5;color:#fff;text-decoration:none;border-radius:4px;">下载更新</a>';
                }
            } else if (hasUpdate === false) {
                h += '<span style="color:#888;">已是最新版本</span>';
            } else {
                h += '<span style="color:#999;">检查更新失败，请访问官网</span>';
            }
            h += '</div>';
            self.$.main.setContent(h);
        });
    },
    _checkUpdate: function(callback) {
        BiliNet.getJson("http://www.biliclassic.cn/webos/api/version.json", function(err, data) {
            if (err || !data || !data.version) { if (callback) callback(null, null, null); return; }
            var current = "0.2.0".split('.').map(function(s){return parseInt(s,10);});
            var remote = (data.version || "0.0.0").split('.').map(function(s){return parseInt(s,10);});
            var hasUpdate = false;
            for (var i = 0; i < 3; i++) {
                if ((remote[i] || 0) > (current[i] || 0)) { hasUpdate = true; break; }
                if ((remote[i] || 0) < (current[i] || 0)) break;
            }
            if (callback) callback(hasUpdate ? true : false, data.version, data);
        });
    },
    showTab: function(inSender) {
        this._currentTab = inSender.tabId;
        if (inSender.tabId === "recommend") { this.loadRecommend(); }
        else if (inSender.tabId === "history") { this.loadHistory(); }
    }
});
