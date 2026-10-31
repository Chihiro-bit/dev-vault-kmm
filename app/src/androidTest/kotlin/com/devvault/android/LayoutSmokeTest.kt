package com.devvault.android

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.test.InstrumentationTestCase
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import com.devvault.model.Entry
import com.devvault.model.ExtraField
import com.devvault.model.TodoInput
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs only against the separate .uitest package with synthetic data. */
@Suppress("DEPRECATION")
class LayoutSmokeTest : InstrumentationTestCase() {
    private lateinit var activity: MainActivity
    private fun ui(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitFor(label: String, condition: () -> Boolean) {
        val until=System.currentTimeMillis()+15000
        while(System.currentTimeMillis()<until) { instrumentation.waitForIdleSync(); var yes=false; ui { yes=condition() }; if(yes) return; Thread.sleep(100) }
        fail("Timed out: $label")
    }
    private fun dialogs():List<Dialog> {
        val field=VaultActivity::class.java.getDeclaredField("dialogs").apply { isAccessible=true }
        @Suppress("UNCHECKED_CAST") return (field.get(activity) as Set<Dialog>).toList()
    }
    private fun views(root:View):List<View> = listOf(root) + if(root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun find(text:String):View? = (dialogs().lastOrNull()?.window?.decorView ?: activity.window.decorView).let(::views).firstOrNull {
        it.isShown && ((it is TextView && it.text.toString()==text) || it.contentDescription?.toString()==text)
    }
    /** 文字常在胶囊/行容器内部，点击最近的可点击祖先 */
    private fun clickable(view:View):View = generateSequence(view) { it.parent as? View }.firstOrNull { it.isClickable } ?: view
    private fun click(text:String) { waitFor(text) { find(text)!=null }; ui { assertTrue("click $text",clickable(find(text)!!).performClick()) }; instrumentation.waitForIdleSync() }
    private fun shot(name:String) {
        ui { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE); dialogs().forEach { it.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) } }
        instrumentation.waitForIdleSync(); Thread.sleep(300)
        val image=instrumentation.uiAutomation.takeScreenshot() ?: error("No screenshot")
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"$name.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
    }
    fun testOriginalPageStructureAndInteractions() {
        assertTrue("Must use isolated test package",instrumentation.targetContext.packageName.endsWith(".uitest"))
        val intent=Intent().setClassName(instrumentation.targetContext.packageName,MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity=instrumentation.startActivitySync(intent) as MainActivity
        waitFor("store loaded") { activity.store.version.value>0 }
        val latch=CountDownLatch(1)
        ui { activity.store.mutate<Unit>(onDone={ latch.countDown() }) {
            val project=saveProject(null,"商城项目","#3b6cff","验收示例项目")
            saveEntry(Entry(projectId=project,title="商城测试账号",env="测试",username="qa@example.com",password="example-only-123",url="https://example.com/login",verifyCode="123456",notes="用于验收的虚构记录",tags="测试,商城",extra=listOf(ExtraField("角色","管理员",false))))
            createTodo(TodoInput(projectId=project,title="验证登录流程",notes="检查账号登录和错误提示",tags="测试"))
            Unit
        } }
        assertTrue("Seed loaded",latch.await(15,TimeUnit.SECONDS)); instrumentation.waitForIdleSync()
        waitFor("entry card") { find("商城测试账号")!=null }
        assertNotNull(findOnMain("复制密码")); shot("01-entries")
        click("新建记录"); waitFor("full-screen edit") { find("保存")!=null }
        ui { val d=dialogs().last(); assertEquals(activity.window.decorView.width,d.window!!.decorView.width); assertNotNull(find("类型")); assertNotNull(find("环境")) }; shot("02-entry-edit")
        ui { dialogs().last().dismiss() }; click("待办事项"); waitFor("todo stats") { find("进行中")!=null }; shot("03-todos")
        // 横屏 / 小窗时待办行可能在可视区域外，先滚到列表末尾
        ui { activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list).let { it.scrollToPosition(it.adapter!!.itemCount-1) } }; waitFor("todo row") { find("验证登录流程")!=null }
        ui { val title=find("验证登录流程")!!; (title.parent.parent as View).performClick() }; waitFor("todo detail") { find("进展记录")!=null }; shot("04-todo-detail")
        ui { dialogs().last().dismiss() }; click("设置"); waitFor("inline login") { find("服务器地址")!=null }; shot("05-settings")
        click("注册"); waitFor("register fields") { find("确认主密码")!=null }; shot("06-register")
        ui { activity.finish() }
    }
    private fun findOnMain(text:String):View? { var result:View?=null; ui { result=find(text) }; return result }
}
