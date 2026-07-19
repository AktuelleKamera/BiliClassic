enyo.kind({
    name: "myapp.VideoPlayer",
    kind: "Control",
    classes: "video-player",
    published: { src: "", title: "", upName: "", playStr: "", likeStr: "" },
    components: [
        { name: "header", classes: "vp-header", components: [
            { name: "titleLbl", classes: "vp-title" },
            { name: "infoLbl", classes: "vp-info" }
        ]},
        { name: "videoContainer", classes: "vp-video-container", components: [
            { name: "video", kind: "Control", tag: "video", attributes: { src: "" }, classes: "vp-video" }
        ]},
        { name: "controls", classes: "vp-controls", components: [
            { name: "playBtn", kind: "onyx.Button", content: "▶ 播放", ontap: "togglePlay", classes: "vp-btn" },
            { name: "seekBarContainer", classes: "vp-seek-container", components: [
                { name: "seekBar", kind: "Control", tag: "input", attributes: { type: "range", min: 0, max: 1000, value: 0 }, classes: "vp-seekbar", onchange: "seekChanged", oninput: "seekChanging" }
            ]},
            { name: "timeLbl", content: "0:00 / 0:00", classes: "vp-time" },
            { name: "sysBtn", kind: "onyx.Button", content: "系统播放", ontap: "openSystemPlayer", classes: "vp-btn" },
            { name: "backBtn", kind: "onyx.Button", content: "返回", ontap: "goBack", classes: "vp-btn" }
        ]}
    ],
    _videoEl: null,
    _updating: false,
    create: function() {
        this.inherited(arguments);
        this._videoEl = this.$.video.hasNode();
        this.$.video.setAttribute("src", this.getSrc());
        this.$.titleLbl.setContent(this.getTitle());
        this.$.infoLbl.setContent(this.getUpName() + " | " + this.getPlayStr() + " | " + this.getLikeStr());
    },
    rendered: function() {
        this.inherited(arguments);
        this._videoEl = this.$.video.hasNode();
        if (!this._videoEl) return;
        var self = this;
        this._videoEl.addEventListener("timeupdate", function() { self.updateTime(); });
        this._videoEl.addEventListener("loadedmetadata", function() { self.updateTime(); });
        this._videoEl.addEventListener("play", function() { self.$.playBtn.setContent("⏸ 暂停"); });
        this._videoEl.addEventListener("pause", function() { self.$.playBtn.setContent("▶ 播放"); });
        this._videoEl.addEventListener("ended", function() { self.$.playBtn.setContent("▶ 播放"); });
    },
    togglePlay: function() {
        if (!this._videoEl) return;
        if (this._videoEl.paused) {
            this._videoEl.play();
            this.$.playBtn.setContent("⏸ 暂停");
        } else {
            this._videoEl.pause();
            this.$.playBtn.setContent("▶ 播放");
        }
    },
    updateTime: function() {
        if (!this._videoEl || this._updating) return;
        var cur = this._videoEl.currentTime || 0;
        var dur = this._videoEl.duration || 0;
        this.$.timeLbl.setContent(this._fmt(cur) + " / " + this._fmt(dur));
        if (dur > 0) {
            this.$.seekBar.setAttribute("max", Math.floor(dur * 1000));
            this.$.seekBar.setAttribute("value", Math.floor(cur * 1000));
        }
    },
    seekChanged: function() {
        if (!this._videoEl) return;
        this._updating = true;
        var val = parseInt(this.$.seekBar.getAttribute("value"), 10) || 0;
        this._videoEl.currentTime = val / 1000;
        this._updating = false;
    },
    seekChanging: function() {
        if (!this._videoEl) return;
        var val = parseInt(this.$.seekBar.getAttribute("value"), 10) || 0;
        this.$.timeLbl.setContent(this._fmt(val / 1000) + " / " + this._fmt(this._videoEl.duration || 0));
    },
    openSystemPlayer: function() {
        var proxyUrl = "http://127.0.0.1:18081/proxy/video?url=" + encodeURIComponent(this.getSrc());
        window.open(proxyUrl, "_blank");
    },
    goBack: function() { app.view.showMain(); },
    setVideoSrc: function(src) {
        this.setSrc(src);
        if (this._videoEl) this._videoEl.src = src;
    },
    _fmt: function(s) {
        s = Math.floor(s || 0);
        var m = Math.floor(s / 60);
        var sec = s % 60;
        return m + ":" + (sec < 10 ? "0" : "") + sec;
    }
});
