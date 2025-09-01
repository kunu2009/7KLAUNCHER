package com.sevenk.launcher.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlin.math.max
import kotlin.math.min

/**
 * Helper class for widget resizing functionality
 */
class WidgetResizeHelper(private val context: Context) {
    
    companion object {
        private const val RESIZE_HANDLE_SIZE = 48f // dp
        private const val MIN_WIDGET_SIZE = 72f // dp
    }
    
    private val resizeHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
    }
    
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    
    /**
     * Calculate widget size in grid cells
     */
    fun calculateWidgetSize(widgetInfo: AppWidgetProviderInfo, cellWidth: Int, cellHeight: Int): Pair<Int, Int> {
        val minWidth = widgetInfo.minWidth
        val minHeight = widgetInfo.minHeight
        
        val cellsWidth = max(1, (minWidth + cellWidth / 2) / cellWidth)
        val cellsHeight = max(1, (minHeight + cellHeight / 2) / cellHeight)
        
        return Pair(cellsWidth, cellsHeight)
    }
    
    /**
     * Create resizable widget container
     */
    fun createResizableWidget(
        widgetView: AppWidgetHostView,
        onResize: (width: Int, height: Int) -> Unit
    ): ResizableWidgetContainer {
        return ResizableWidgetContainer(context, widgetView, onResize)
    }
    
    /**
     * Check if point is in resize handle area
     */
    fun isInResizeHandle(x: Float, y: Float, viewWidth: Int, viewHeight: Int): Boolean {
        val handleSize = RESIZE_HANDLE_SIZE * context.resources.displayMetrics.density
        val handleX = viewWidth - handleSize
        val handleY = viewHeight - handleSize
        
        return x >= handleX && y >= handleY
    }
    
    /**
     * Draw resize handles on widget
     */
    fun drawResizeHandles(canvas: Canvas, width: Int, height: Int, isSelected: Boolean) {
        if (!isSelected) return
        
        val density = context.resources.displayMetrics.density
        val handleSize = RESIZE_HANDLE_SIZE * density
        
        // Draw border around widget
        val borderRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRect(borderRect, borderPaint)
        
        // Draw resize handle in bottom-right corner
        val handleX = width - handleSize
        val handleY = height - handleSize
        val handleRect = RectF(handleX, handleY, width.toFloat(), height.toFloat())
        canvas.drawRect(handleRect, resizeHandlePaint)
        
        // Draw resize icon (diagonal lines)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 2f * density
        }
        
        val centerX = handleX + handleSize / 2f
        val centerY = handleY + handleSize / 2f
        val lineOffset = 8f * density
        
        // Draw diagonal lines
        canvas.drawLine(
            centerX - lineOffset, centerY + lineOffset,
            centerX + lineOffset, centerY - lineOffset,
            linePaint
        )
        canvas.drawLine(
            centerX - lineOffset / 2f, centerY + lineOffset,
            centerX + lineOffset, centerY - lineOffset / 2f,
            linePaint
        )
        canvas.drawLine(
            centerX - lineOffset, centerY + lineOffset / 2f,
            centerX + lineOffset / 2f, centerY - lineOffset,
            linePaint
        )
    }
    
    /**
     * Calculate new widget size during resize
     */
    fun calculateResizeSize(
        startWidth: Int,
        startHeight: Int,
        deltaX: Float,
        deltaY: Float,
        cellWidth: Int,
        cellHeight: Int
    ): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val minSize = MIN_WIDGET_SIZE * density
        
        val newWidth = max(minSize, startWidth + deltaX)
        val newHeight = max(minSize, startHeight + deltaY)
        
        // Snap to grid
        val cellsWidth = max(1, (newWidth / cellWidth).toInt())
        val cellsHeight = max(1, (newHeight / cellHeight).toInt())
        
        return Pair(cellsWidth * cellWidth, cellsHeight * cellHeight)
    }
}

/**
 * Container view that makes widgets resizable
 */
class ResizableWidgetContainer(
    context: Context,
    private val widgetView: AppWidgetHostView,
    private val onResize: (width: Int, height: Int) -> Unit
) : ViewGroup(context) {
    
    private val resizeHelper = WidgetResizeHelper(context)
    private var isSelected = false
    private var isResizing = false
    private var startX = 0f
    private var startY = 0f
    private var startWidth = 0
    private var startHeight = 0
    
    init {
        addView(widgetView)
        setWillNotDraw(false)
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        
        setMeasuredDimension(width, height)
        
        // Measure widget view
        val widgetWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val widgetHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        widgetView.measure(widgetWidthSpec, widgetHeightSpec)
    }
    
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        widgetView.layout(0, 0, r - l, b - t)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        resizeHelper.drawResizeHandles(canvas, width, height, isSelected)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (resizeHelper.isInResizeHandle(event.x, event.y, width, height)) {
                    isResizing = true
                    startX = event.x
                    startY = event.y
                    startWidth = width
                    startHeight = height
                    return true
                } else {
                    isSelected = !isSelected
                    invalidate()
                    return true
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isResizing) {
                    val deltaX = event.x - startX
                    val deltaY = event.y - startY
                    
                    val cellWidth = 100 // This should come from grid configuration
                    val cellHeight = 100
                    
                    val (newWidth, newHeight) = resizeHelper.calculateResizeSize(
                        startWidth, startHeight, deltaX, deltaY, cellWidth, cellHeight
                    )
                    
                    if (newWidth != width || newHeight != height) {
                        onResize(newWidth, newHeight)
                    }
                    return true
                }
            }
            
            MotionEvent.ACTION_UP -> {
                if (isResizing) {
                    isResizing = false
                    return true
                }
            }
        }
        
        return super.onTouchEvent(event)
    }
    
    override fun setSelected(selected: Boolean) {
        isSelected = selected
        invalidate()
    }
}
