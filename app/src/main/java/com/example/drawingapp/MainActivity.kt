package com.example.drawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.Image
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.activity_main.ib_gallery
import kotlinx.android.synthetic.main.dialog_brush_size.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class MainActivity : AppCompatActivity() {
    private var customProgress:Dialog?=null
    private val openGallery:ActivityResultLauncher<Intent> =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
                result->
                if(result.resultCode== RESULT_OK && result.data!=null){
                    val galleryImage:ImageView=findViewById(R.id.iv_background)
                    galleryImage.setImageURI(result.data?.data)
                }
            }
    private val requestPermission:ActivityResultLauncher<Array<String>> =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                permissions.entries.forEach {
                    val permissionName = it.key
                    val isGranted = it.value
                    if (isGranted) {
                        Toast.makeText(
                            this@MainActivity,
                            "Permission Granted For Accessing Files",
                            Toast.LENGTH_SHORT).show()
                        val intentPicker=Intent(Intent.ACTION_PICK,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        openGallery.launch(intentPicker)
                    } else if (permissionName == Manifest.permission.READ_EXTERNAL_STORAGE) {
                        Toast.makeText(
                            this@MainActivity,
                            "Oops You have denied for Accessing files",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }


    private var drawingView:DrawingView?=null
    private var mImageButtonCurrent: ImageButton? =null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView=findViewById(R.id.drawing_view)
        var LinearLayoutPaintColours=findViewById<LinearLayout>(R.id.paint_colours)
        mImageButtonCurrent=LinearLayoutPaintColours[2] as ImageButton
        mImageButtonCurrent!!.setImageDrawable(
            ContextCompat.getDrawable(this,R.drawable.img_palet_selected)
        )

        val ib_brush:ImageButton=findViewById(R.id.ib_brush)
        ib_brush.setOnClickListener{
            brushSizeChooserDialog()
        }
        val ib_image:ImageButton=findViewById(R.id.ib_gallery)
        ib_gallery.setOnClickListener {
            requestStoragePermission()

        }
        val ib_undo:ImageButton=findViewById(R.id.ib_undo)
        ib_undo.setOnClickListener {
            drawingView?.onClickUndo()
        }
        val ib_redo:ImageButton=findViewById(R.id.ib_redo)
        ib_redo.setOnClickListener {
            drawingView?.onClickRedo()
        }
        val ib_save:ImageButton=findViewById(R.id.ib_save)
        ib_save.setOnClickListener {
            customProgressDialog()
            if(isReadStorageAllowed()){
                lifecycleScope.launch {
                    val flDrawingView:FrameLayout=findViewById(R.id.fl_drawing_view_container)
//                    val mBitmap:Bitmap=getBitMapFromView()
//                    saveBitMapFile(mBitmap)
                    saveBitMapFile(getBitMapFromView(flDrawingView))
                }
            }

        }
    }
    private fun brushSizeChooserDialog(){
        val brushDialog=Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("set Brush Size: ")
        val smallBtn=brushDialog.ib_small_brush
        val mediumBtn=brushDialog.ib_medium_brush
        val largeBtn=brushDialog.ib_large_brush
        smallBtn.setOnClickListener{
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        mediumBtn.setOnClickListener{
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        largeBtn.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }
    fun paintClicked(view: View) {
        if(view!=mImageButtonCurrent) {
            var imageButton = view as ImageButton
            var colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)
            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.img_palet_selected)
            )
            mImageButtonCurrent?.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.img_palet)
            )
            mImageButtonCurrent = view
        }
    }
    private fun requestStoragePermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
        ){
            showRationaleDialog("Kids Drawing App",
            "Kids Drawing App needs to access your external storage")
        }else{
            requestPermission.launch(arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ))
        }
    }
    private fun showRationaleDialog(
        tittle:String,
        message:String){
        val builder:AlertDialog.Builder=AlertDialog.Builder(this)
        builder.setTitle(tittle)
        builder.setMessage(message)
        builder.setIcon(R.drawable.alert)
        builder.setPositiveButton("cancel"){
            dialog,_->
            dialog.dismiss()
        }
        builder.create().show()
    }
    private fun isReadStorageAllowed():Boolean{
        val result=ContextCompat.checkSelfPermission(this@MainActivity,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        // return true if result is equal to permission granted i.e. true(0)
        return result==PackageManager.PERMISSION_GRANTED

    }
    private fun getBitMapFromView(view: View):Bitmap{
        // Define a Bitmap with the same size as view
        //CreateBitmap:Returns a Mutable bitmap with the specified height and width
        val returnedBitMap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        // bind a canvas to it
        val canvas=Canvas(returnedBitMap)
        // get the view's background
        val bgDrawable=view.background
        if(bgDrawable!=null){
            // has background drawable, then draw it on canvas
            bgDrawable.draw(canvas)
        }else{
            // does not have background drawable then draw white colour on canvas
            canvas.drawColor(Color.WHITE)
        }
        // draw the view on the canvas
        view.draw(canvas)
        // return the bitmap
        return returnedBitMap
    }
    private suspend fun saveBitMapFile(mBitmap:Bitmap):String{
        var result=""
        withContext(Dispatchers.IO){
            if(mBitmap!=null){
                try {
                    val bytes=ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG,90,bytes)
                    val f:File= File(externalCacheDir?.absoluteFile.toString()
                    +File.separator+"KidsDrawingApp_"+System.currentTimeMillis()/1000+".png")
                    val fo=FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()
                    result=f.absolutePath
                    runOnUiThread {
                        cancelCustomProgress()
                        if(result.isNotEmpty()){
                            Toast.makeText(this@MainActivity,
                                "File Saved Successfully:$result",
                            Toast.LENGTH_SHORT).show()
                            shareImage(result)
                        }else{
                            Toast.makeText(this@MainActivity,
                                "Something went wrong while saving the file",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                catch (e:Exception){
                    result=""
                    e.printStackTrace()
                }
            }
        }
        return result
    }
    private fun cancelCustomProgress(){
        if(customProgress!=null){
            customProgress?.dismiss()
            customProgress=null
        }

    }
    private fun customProgressDialog(){
        customProgress=Dialog(this@MainActivity)
        customProgress?.setContentView(R.layout.progress_bar)
        customProgress?.show()
    }
    private fun shareImage(result:String){
        MediaScannerConnection.scanFile(this, arrayOf(result),null){
            path,uri->
            val shareIntent=Intent()
            shareIntent.action=Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM,uri)
            shareIntent.type="image/png"
            startActivity(Intent.createChooser(shareIntent,"Share.."))
        }
    }
}