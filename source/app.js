/**
	Define and instantiate your enyo.Application kind in this file.  Note,
	application rendering should be deferred until DOM is ready by wrapping
	it in a call to enyo.ready().
*/

enyo.kind({
	name: "myapp.Application",
	kind: "enyo.Application",
	view: "myapp.MainView",
	create: function() {
		this.inherited(arguments);
		if (window.PalmSystem) {
			setTimeout(function() { PalmSystem.stageReady(); }, 1);
		}
	}
});

enyo.ready(function () {
	new myapp.Application({name: "app"});
});
