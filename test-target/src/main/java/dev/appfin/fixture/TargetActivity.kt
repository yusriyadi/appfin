package dev.appfin.fixture

class TargetActivity : android.app.Activity() {
    override fun onCreate(state: android.os.Bundle?) {
        super.onCreate(state)
        setContentView(android.widget.TextView(this).apply {
            text = "AppFin Test Target\nAplikasi kosong untuk pengujian force stop."
            gravity = android.view.Gravity.CENTER
        })
    }
}
