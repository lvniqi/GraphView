/**
 * This file is part of GraphView.
 *
 * GraphView is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GraphView is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GraphView.  If not, see <http://www.gnu.org/licenses/lgpl.html>.
 *
 * Copyright Jonas Gehring
 */

package com.jjoe64.graphview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.jjoe64.graphview.GraphViewSeries.GraphViewSeriesStyle;
import com.jjoe64.graphview.compatible.ScaleGestureDetector;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;

/**
 * GraphView is a Android View for creating zoomable and scrollable graphs.
 * This is the abstract base class for all graphs. Extend this class and implement {@link #drawSeries(android.graphics.Canvas, GraphViewDataInterface[], float, float, float, double, double, double, double, float, com.jjoe64.graphview.GraphViewSeries.GraphViewSeriesStyle)} to display a custom graph.
 * Use {@link com.jjoe64.graphview.LineGraphView} for creating a line chart.
 *
 * @author jjoe64 - jonas gehring - http://www.jjoe64.com
 *         <p/>
 *         Copyright (C) 2011 Jonas Gehring
 *         Licensed under the GNU Lesser General Public License (LGPL)
 *         http://www.gnu.org/licenses/lgpl.html
 */
abstract public class GraphView extends LinearLayout {
    static final private class GraphViewConfig {
        static final float BORDER = 20;
    }

    private class GraphViewContentView extends View {
        private int lastTouchEventX;
        private int lastTouchEventY;
        private float graphwidth;
        private float graphheight;
        private boolean scrollingStarted;
        //触摸点宽度
        private float pointer_width = 0;
        //触摸宽度模式(x1>x0 or x0>x1)
        private boolean pointer_width_mode;

        //触摸点宽度
        private float pointer_height = 0;
        //触摸宽度模式(x1>x0 or x0>x1)
        private boolean pointer_height_mode;

        //触摸模式
        private int touch_mode = 0;
        /**
         * @param context
         */
        public GraphViewContentView(Context context) {
            super(context);
            setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        }

        /**
         * @param canvas
         */
        @Override
        protected void onDraw(Canvas canvas) {

            paint.setAntiAlias(true);

            // normal
            paint.setStrokeWidth(0);

            float border = GraphViewConfig.BORDER;
            float horstart = 0;
            float height = getHeight();
            float width = getWidth() - 1;
            double maxY = getMaxY();
            double minY = getMinY();
            double maxX = getMaxX(false);
            double minX = getMinX(false);
            double diffX = maxX - minX;

            // measure bottom text
            if (labelTextHeight == null || horLabelTextWidth == null) {
                paint.setTextSize(getGraphViewStyle().getTextSize());
                double testX = ((getMaxX(true) - getMinX(true)) * 0.783) + getMinX(true);
                String testLabel = formatLabel(testX, true);
                paint.getTextBounds(testLabel, 0, testLabel.length(), textBounds);
                // multiline
                int lines = 1;
                for (byte c : testLabel.getBytes()) {
                    if (c == '\n') lines++;
                }
                labelTextHeight = textBounds.height() * lines;
                horLabelTextWidth = (textBounds.width());
            }
            border += labelTextHeight;

            graphheight = height - (2 * border);
            graphwidth = width;

            if (horlabels == null) {
                horlabels = generateHorlabels(graphwidth);
            } else if (getGraphViewStyle().getNumHorizontalLabels() > 0) {
                Log.w("GraphView", "when you use static labels (via setHorizontalLabels) the labels will just be shown exactly in that way, that you have set it. setNumHorizontalLabels does not have any effect.");
            }
            if (verlabels == null) {
                verlabels = generateVerlabels(graphheight);
            } else if (getGraphViewStyle().getNumVerticalLabels() > 0) {
                Log.w("GraphView", "when you use static labels (via setVerticalLabels) the labels will just be shown exactly in that way, that you have set it. setNumVerticalLabels does not have any effect.");
            }

            // horizontal lines
            if (graphViewStyle.getGridStyle().drawHorizontal()) {
                paint.setTextAlign(Align.LEFT);
                int vers = verlabels.length - 1;
                for (int i = 0; i < verlabels.length; i++) {
                    paint.setColor(graphViewStyle.getGridColor());
                    float y = ((graphheight / vers) * i) + border;
                    canvas.drawLine(horstart, y, width, y, paint);
                }
            }

            drawHorizontalLabels(canvas, border, horstart, height, horlabels, graphwidth);

            paint.setColor(graphViewStyle.getHorizontalLabelsColor());
            paint.setTextAlign(Align.CENTER);
            canvas.drawText(title, (graphwidth / 2) + horstart, border - 4, paint);

            if (maxY == minY) {
                // if min/max is the same, fake it so that we can render a line
                if (maxY == 0) {
                    // if both are zero, change the values to prevent division by zero
                    maxY = 1.0d;
                    minY = 0.0d;
                } else {
                    maxY = maxY * 1.05d;
                    minY = minY * 0.95d;
                }
            }

            double diffY = maxY - minY;
            paint.setStrokeCap(Paint.Cap.ROUND);

            for (int i = 0; i < graphSeries.size(); i++) {
                drawSeries(canvas, _values(i), graphwidth, graphheight, border, minX, minY, diffX, diffY, horstart, graphSeries.get(i).style);
                //if someone want to show max
                double y_min = border + graphheight - graphheight * ((graphSeries.get(i).GetSeriesMin()) - minY) / diffY;
                double y_max = border + graphheight - graphheight * ((graphSeries.get(i).GetSeriesMax()) - minY) / diffY;
                if(graphSeries.get(i).GetShowMaxFlag()) {
                    if (y_max > border && y_max < graphheight + border) {
                        paint.setColor(graphSeries.get(i).style.color);
                        paint.setStrokeWidth(0);
                        for (int j = 0; j < graphwidth; j += 25) {
                            canvas.drawLine(j, (float) (y_max - 2), (float) j + 5, (float) (y_max - 2), paint);
                        }
                    }
                }
                //if someone want to show min
                if(graphSeries.get(i).GetShowMinFlag()) {
                    if (y_min > border && y_min < graphheight + border) {
                        paint.setColor(graphSeries.get(i).style.color);
                        paint.setStrokeWidth(0);
                        for (int j = 0; j < graphwidth; j += 25) {
                            canvas.drawLine(j, (float) (y_min - 2), (float) j + 5, (float) (y_min - 2), paint);
                        }
                    }
                }
                //if someone want to sign the curve
                if(graphSeries.get(i).GetSignCurveFlag()) {
                    paint.setColor(graphSeries.get(i).style.color);
                    paint.setStrokeWidth(0);
                    if (y_min < border) {
                        final int size = 20;
                        Path path = new Path();
                        path.moveTo(horstart + graphwidth / 2 + size * (3 * i - 1), border + size);// 此点为多边形的起点
                        path.lineTo(horstart + graphwidth / 2 + size * (3 * i), border);
                        path.lineTo(horstart + graphwidth / 2 + size * (3 * i + 1), border + size);
                        path.close(); // 使这些点构成封闭的多边形
                        canvas.drawPath(path, paint);
                    } else if (y_max > graphheight + border) {
                        final int size = 20;
                        Path path = new Path();
                        path.moveTo(horstart + graphwidth / 2 + size * (3 * i - 1), graphheight + border - size);// 此点为多边形的起点
                        path.lineTo(horstart + graphwidth / 2 + size * (3 * i), graphheight + border);
                        path.lineTo(horstart + graphwidth / 2 + size * (3 * i + 1), graphheight + border - size);
                        path.close(); // 使这些点构成封闭的多边形
                        canvas.drawPath(path, paint);
                    }
                }
            }
            if (showLegend) drawLegend(canvas, height, width);
        }
        private void onMoveGesture(int dx,int dy) {
            // view port update
            if (viewportSize != 0) {
                viewportStart -= dx * viewportSize / graphwidth;

                // minimal and maximal view limit
                double minX = getMinX(true);
                double maxX = getMaxX(true);
                double dy_max = getMinY()-getMaxY();
                if (viewportStart < minX) {
                    viewportStart = minX;
                } else if (viewportStart + viewportSize > maxX) {
                    viewportStart = maxX - viewportSize;
                }
                if (dy > 2 || dy < -2){
                    manualMinYValue -= dy * dy_max/graphheight ;
                    manualMaxYValue -= dy * dy_max/graphheight ;
                }

                // labels have to be regenerated
                if (!staticHorizontalLabels) horlabels = null;
                if (!staticVerticalLabels) verlabels = null;
                viewVerLabels.invalidate();
            }
            invalidate();
        }

        private void OnPointersTouch(MotionEvent event){

            boolean change = false;
            float now_x =event.getX(0) - event.getX(1);
            float now_y =event.getY(0) - event.getY(1);
            //if the point1 and point0 don't have cross
            if( (now_x >0 && pointer_width_mode)
                    ||(now_x <0 && !pointer_width_mode)) {
                if (Math.abs(pointer_width) > 10
                        && Math.abs(now_x) > 10
                        && Math.abs(pointer_width-now_x) >4
                        && Math.abs(pointer_height-now_y)<Math.abs(pointer_width-now_x)
                        ) {
                    double minX = getMinX(true);
                    double maxX = getMaxX(true);
                    //goto center
                    viewportStart += viewportSize / 2;
                    viewportSize *= Math.abs(pointer_width) / Math.abs(now_x);
                    //goto back
                    viewportStart -= viewportSize / 2;
                    if (viewportStart < minX) {
                        viewportStart = minX;
                    }
                    if (viewportStart + viewportSize > maxX) {
                        viewportSize = maxX - viewportStart;
                    }
                    change = true;
                }
            }
            if( (now_y >0 && pointer_height_mode)
                    ||(now_y <0 && !pointer_height_mode)) {
                if (Math.abs(pointer_height) > 15
                        && Math.abs(now_y) > 15
                        && Math.abs(pointer_height-now_y) >4
                        && Math.abs(pointer_height-now_y)>Math.abs(pointer_width-now_x)
                    ) {
                    double minY = getMinY();
                    double maxY = getMaxY();
                    double size = manualMaxYValue-manualMinYValue;
                    double middle = (manualMinYValue + manualMaxYValue) /2;
                    size *= pointer_height/now_y;
                    manualMinYValue = middle - size/2 ;
                    manualMaxYValue = middle + size/2 ;
                    change = true;

                }
            }
            //save width
            pointer_width = now_x;
            pointer_height = now_y;
            if(change){
                if (!staticHorizontalLabels) horlabels = null;
                if (!staticVerticalLabels) verlabels = null;
                viewVerLabels.invalidate();
                invalidate();
            }
        }
        /**
         * @param event
         */
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!isScrollable() || isDisableTouch()) {
                return super.onTouchEvent(event);
            }
            boolean handled = false;
            switch (event.getAction()&MotionEvent.ACTION_MASK){
            //Log.d("GraphView", "on touch event scale not handled+"+lastTouchEventX);
            // if not scaled, scroll
                case MotionEvent.ACTION_DOWN:
                    touch_mode = 1;
                    scrollingStarted = true;
                    handled = true;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    touch_mode += 1;
                    scrollingStarted = false;
                    lastTouchEventX = 0;
                    lastTouchEventY = 0;
                    pointer_width = event.getX(0) - event.getX(1);
                    pointer_height = event.getY(0) - event.getY(1);
                    if(pointer_width>0){
                        pointer_width_mode = true;
                    }
                    else {
                        pointer_width_mode = false;
                    }
                    if(pointer_height>0){
                        pointer_height_mode = true;
                    }
                    else {
                        pointer_height_mode = false;
                    }
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    touch_mode -= 1;
                    if( touch_mode == 1) {
                        scrollingStarted = true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    touch_mode = 0;
                    scrollingStarted = false;
                    lastTouchEventX = 0;
                    lastTouchEventY = 0;
                    handled = true;
                case MotionEvent.ACTION_MOVE:
                        if (touch_mode >= 2 &&scalable) {
                            OnPointersTouch(event);
                        }
                        else if(scrollingStarted) {

                            if (lastTouchEventX != 0 || lastTouchEventY != 0) {
                                int x_different = (int) event.getX() - lastTouchEventX;
                                int y_different = (int) event.getY() - lastTouchEventY;
                                if (Math.abs(x_different) > Math.abs(y_different)) {
                                    onMoveGesture(x_different, 0);
                                }
                                else{
                                    onMoveGesture(0, y_different);
                                }
                            }
                            lastTouchEventX = (int) event.getX();
                            lastTouchEventY = (int) event.getY();
                            handled = true;
                        }break;

            }
            if (handled) {
                invalidate();
            }
            return handled;
        }
    }

    /**
     * one data set for a graph series
     */
    static public class GraphViewData implements GraphViewDataInterface {
        public final double valueX;
        public final double valueY;

        public GraphViewData(double valueX, double valueY) {
            super();
            this.valueX = valueX;
            this.valueY = valueY;
        }

        @Override
        public double getX() {
            return valueX;
        }

        @Override
        public double getY() {
            return valueY;
        }
    }

    public enum LegendAlign {
        TOP, MIDDLE, BOTTOM
    }

    private class VerLabelsView extends View {
        /**
         * @param context
         */
        public VerLabelsView(Context context) {
            super(context);
            setLayoutParams(new LayoutParams(
                    getGraphViewStyle().getVerticalLabelsWidth() == 0 ? 100 : getGraphViewStyle().getVerticalLabelsWidth()
                    , LayoutParams.FILL_PARENT));
        }

        /**
         * @param canvas
         */
        @Override
        protected void onDraw(Canvas canvas) {
            // normal
            paint.setStrokeWidth(0);

            // measure bottom text
            if (labelTextHeight == null || verLabelTextWidth == null) {
                paint.setTextSize(getGraphViewStyle().getTextSize());
                double testY = ((getMaxY() - getMinY()) * 0.783) + getMinY();
                String testLabel = formatLabel(testY, false);
                paint.getTextBounds(testLabel, 0, testLabel.length(), textBounds);
                labelTextHeight = (textBounds.height());
                verLabelTextWidth = (textBounds.width());
            }
            if (getGraphViewStyle().getVerticalLabelsWidth() == 0 && getLayoutParams().width != verLabelTextWidth + GraphViewConfig.BORDER) {
                setLayoutParams(new LayoutParams(
                        (int) (verLabelTextWidth + GraphViewConfig.BORDER), LayoutParams.FILL_PARENT));
            } else if (getGraphViewStyle().getVerticalLabelsWidth() != 0 && getGraphViewStyle().getVerticalLabelsWidth() != getLayoutParams().width) {
                setLayoutParams(new LayoutParams(
                        getGraphViewStyle().getVerticalLabelsWidth(), LayoutParams.FILL_PARENT));
            }

            float border = GraphViewConfig.BORDER;
            border += labelTextHeight;
            float height = getHeight();
            float graphheight = height - (2 * border);

            if (verlabels == null) {
                verlabels = generateVerlabels(graphheight);
            } else if (getGraphViewStyle().getNumVerticalLabels() > 0) {
                Log.w("GraphView", "when you use static labels (via setVerticalLabels) the labels will just be shown exactly in that way, that you have set it. setNumVerticalLabels does not have any effect.");
            }

            // vertical labels
            paint.setTextAlign(getGraphViewStyle().getVerticalLabelsAlign());
            int labelsWidth = getWidth();
            int labelsOffset = 0;
            if (getGraphViewStyle().getVerticalLabelsAlign() == Align.RIGHT) {
                labelsOffset = labelsWidth;
            } else if (getGraphViewStyle().getVerticalLabelsAlign() == Align.CENTER) {
                labelsOffset = labelsWidth / 2;
            }
            int vers = verlabels.length - 1;
            for (int i = 0; i < verlabels.length; i++) {
                float y = ((graphheight / vers) * i) + border;
                paint.setColor(graphViewStyle.getVerticalLabelsColor());

                String[] lines = verlabels[i].split("\n");
                for (int li = 0; li < lines.length; li++) {
                    // for the last line y = height
                    float y2 = y - (lines.length - li - 1) * graphViewStyle.getTextSize() * 1.1f;
                    canvas.drawText(lines[li], labelsOffset, y2, paint);
                }
            }

            // reset
            paint.setTextAlign(Align.LEFT);
        }
    }

    protected final Paint paint;
    private String[] horlabels;
    private String[] verlabels;
    private String title;
    private boolean scrollable;
    private boolean disableTouch;
    private double viewportStart;
    private double viewportSize;
    private final View viewVerLabels;
    private ScaleGestureDetector scaleDetector;
    private boolean scalable;
    private final NumberFormat[] numberformatter = new NumberFormat[2];
    private final List<GraphViewSeries> graphSeries;
    private boolean showLegend = false;
    private LegendAlign legendAlign = LegendAlign.MIDDLE;
    private boolean manualYAxis;
    private boolean manualMaxY;
    private boolean manualMinY;
    private double manualMaxYValue;
    private double manualMinYValue;
    protected GraphViewStyle graphViewStyle;
    private final GraphViewContentView graphViewContentView;
    private CustomLabelFormatter customLabelFormatter;
    private Integer labelTextHeight;
    private Integer horLabelTextWidth;
    private Integer verLabelTextWidth;
    private final Rect textBounds = new Rect();
    private boolean staticHorizontalLabels;
    private boolean staticVerticalLabels;
    private boolean showHorizontalLabels = true;
    private boolean showVerticalLabels = true;

    public GraphView(Context context, AttributeSet attrs) {
        this(context, attrs.getAttributeValue(null, "title"));

        int width = attrs.getAttributeIntValue("android", "layout_width", LayoutParams.MATCH_PARENT);
        int height = attrs.getAttributeIntValue("android", "layout_height", LayoutParams.MATCH_PARENT);
        setLayoutParams(new LayoutParams(width, height));
    }

    /**
     * @param context
     * @param title   [optional]
     */
    public GraphView(Context context, String title) {
        super(context);
        setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

        if (title == null)
            this.title = "";
        else
            this.title = title;

        graphViewStyle = new GraphViewStyle();
        graphViewStyle.useTextColorFromTheme(context);

        paint = new Paint();
        graphSeries = new ArrayList<GraphViewSeries>();

        viewVerLabels = new VerLabelsView(context);
        addView(viewVerLabels);
        graphViewContentView = new GraphViewContentView(context);
        addView(graphViewContentView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 1));
    }

    private GraphViewDataInterface[] _values(int idxSeries) {
        GraphViewDataInterface[] values = graphSeries.get(idxSeries).values;
        synchronized (values) {
            if (viewportStart == 0 && viewportSize == 0) {
                // all data
                return values;
            } else {
                // viewport
                List<GraphViewDataInterface> listData = new ArrayList<GraphViewDataInterface>();
                for (int i = 0; i < values.length; i++) {
                    if (values[i].getX() >= viewportStart) {
                        if (values[i].getX() > viewportStart + viewportSize) {
                            listData.add(values[i]); // one more for nice scrolling
                            break;
                        } else {
                            listData.add(values[i]);
                        }
                    } else {
                        if (listData.isEmpty()) {
                            listData.add(values[i]);
                        }
                        listData.set(0, values[i]); // one before, for nice scrolling
                    }
                }
                return listData.toArray(new GraphViewDataInterface[listData.size()]);
            }
        }
    }

    /**
     * add a series of data to the graph
     *
     * @param series
     */
    public void addSeries(GraphViewSeries series) {
        series.addGraphView(this);
        graphSeries.add(series);
        redrawAll();
    }

    protected void drawHorizontalLabels(Canvas canvas, float border,
                                        float horstart, float height, String[] horlabels, float graphwidth) {
        // horizontal labels + lines
        int hors = horlabels.length - 1;
        for (int i = 0; i < horlabels.length; i++) {
            paint.setColor(graphViewStyle.getGridColor());
            float x = ((graphwidth / hors) * i) + horstart;
            if (graphViewStyle.getGridStyle().drawVertical()) { // vertical lines
                canvas.drawLine(x, height - border, x, border, paint);
            }
            if (showHorizontalLabels) {
                paint.setTextAlign(Align.CENTER);
                if (i == horlabels.length - 1)
                    paint.setTextAlign(Align.RIGHT);
                if (i == 0)
                    paint.setTextAlign(Align.LEFT);
                paint.setColor(graphViewStyle.getHorizontalLabelsColor());
                String[] lines = horlabels[i].split("\n");
                for (int li = 0; li < lines.length; li++) {
                    // for the last line y = height
                    float y = (height - 4) - (lines.length - li - 1) * graphViewStyle.getTextSize() * 1.1f;
                    canvas.drawText(lines[li], x, y, paint);
                }
            }
        }
    }

    protected void drawLegend(Canvas canvas, float height, float width) {
        float textSize = paint.getTextSize();
        int spacing = getGraphViewStyle().getLegendSpacing();
        int border = getGraphViewStyle().getLegendBorder();
        int legendWidth = getGraphViewStyle().getLegendWidth();

        int shapeSize = (int) (textSize * 0.8d);
        //Log.d("GraphView", "draw legend size: "+paint.getTextSize());

        // rect
        paint.setARGB(180, 100, 100, 100);
        float legendHeight = (shapeSize + spacing) * graphSeries.size() + 2 * border - spacing;
        float lLeft = width - legendWidth - border * 2;
        float lTop;
        switch (legendAlign) {
            case TOP:
                lTop = 0;
                break;
            case MIDDLE:
                lTop = height / 2 - legendHeight / 2;
                break;
            default:
                lTop = height - GraphViewConfig.BORDER - legendHeight - getGraphViewStyle().getLegendMarginBottom();
        }
        float lRight = lLeft + legendWidth;
        float lBottom = lTop + legendHeight;
        canvas.drawRoundRect(new RectF(lLeft, lTop, lRight, lBottom), 8, 8, paint);

        for (int i = 0; i < graphSeries.size(); i++) {
            paint.setColor(graphSeries.get(i).style.color);
            canvas.drawRect(new RectF(lLeft + border, lTop + border + (i * (shapeSize + spacing)), lLeft + border + shapeSize, lTop + border + (i * (shapeSize + spacing)) + shapeSize), paint);
            if (graphSeries.get(i).description != null) {
                paint.setColor(Color.WHITE);
                paint.setTextAlign(Align.LEFT);
                canvas.drawText(graphSeries.get(i).description, lLeft + border + shapeSize + spacing, lTop + border + shapeSize + (i * (shapeSize + spacing)), paint);
            }
        }
    }

    abstract protected void drawSeries(Canvas canvas, GraphViewDataInterface[] values, float graphwidth, float graphheight, float border, double minX, double minY, double diffX, double diffY, float horstart, GraphViewSeriesStyle style);

    /**
     * formats the label
     * use #setCustomLabelFormatter or static labels if you want custom labels
     *
     * @param value    x and y values
     * @param isValueX if false, value y wants to be formatted
     * @return value to display
     * @deprecated use {@link #setCustomLabelFormatter(CustomLabelFormatter)}
     */
    @Deprecated
    protected String formatLabel(double value, boolean isValueX) {
        if (customLabelFormatter != null) {
            String label = customLabelFormatter.formatLabel(value, isValueX);
            if (label != null) {
                return label;
            }
        }
        int i = isValueX ? 1 : 0;
        if (numberformatter[i] == null) {
            numberformatter[i] = NumberFormat.getNumberInstance();
            double highestvalue = isValueX ? getMaxX(false) : getMaxY();
            double lowestvalue = isValueX ? getMinX(false) : getMinY();
            if (highestvalue - lowestvalue < 0.1) {
                numberformatter[i].setMaximumFractionDigits(6);
            } else if (highestvalue - lowestvalue < 1) {
                numberformatter[i].setMaximumFractionDigits(4);
            } else if (highestvalue - lowestvalue < 20) {
                numberformatter[i].setMaximumFractionDigits(3);
            } else if (highestvalue - lowestvalue < 100) {
                numberformatter[i].setMaximumFractionDigits(1);
            } else {
                numberformatter[i].setMaximumFractionDigits(0);
            }
        }
        return numberformatter[i].format(value);
    }

    private String[] generateHorlabels(float graphwidth) {
        int numLabels = getGraphViewStyle().getNumHorizontalLabels() - 1;
        if (numLabels < 0) { // automatic
            if (graphwidth <= 0) graphwidth = 1f;
            numLabels = (int) (graphwidth / (horLabelTextWidth * 2));
        }

        String[] labels = new String[numLabels + 1];
        double min = getMinX(false);
        double max = getMaxX(false);
        for (int i = 0; i <= numLabels; i++) {
            labels[i] = formatLabel(min + ((max - min) * i / numLabels), true);
        }
        return labels;
    }

    synchronized private String[] generateVerlabels(float graphheight) {
        int numLabels = getGraphViewStyle().getNumVerticalLabels() - 1;
        if (numLabels < 0) { // automatic
            if (graphheight <= 0) graphheight = 1f;
            numLabels = (int) (graphheight / (labelTextHeight * 3));
            if (numLabels == 0) {
                Log.w("GraphView", "Height of Graph is smaller than the label text height, so no vertical labels were shown!");
            }
        }
        String[] labels = new String[numLabels + 1];
        double min = getMinY();
        double max = getMaxY();
        if (max == min) {
            // if min/max is the same, fake it so that we can render a line
            if (max == 0) {
                // if both are zero, change the values to prevent division by zero
                max = 1.0d;
                min = 0.0d;
            } else {
                max = max * 1.05d;
                min = min * 0.95d;
            }
        }

        for (int i = 0; i <= numLabels; i++) {
            labels[numLabels - i] = formatLabel(min + ((max - min) * i / numLabels), false);
        }
        return labels;
    }

    /**
     * @return the custom label formatter, if there is one. otherwise null
     */
    public CustomLabelFormatter getCustomLabelFormatter() {
        return customLabelFormatter;
    }

    /**
     * @return the graphview style. it will never be null.
     */
    public GraphViewStyle getGraphViewStyle() {
        return graphViewStyle;
    }

    /**
     * get the position of the legend
     *
     * @return
     */
    public LegendAlign getLegendAlign() {
        return legendAlign;
    }

    /**
     * @return legend width
     * @deprecated use {@link GraphViewStyle#getLegendWidth()}
     */
    @Deprecated
    public float getLegendWidth() {
        return getGraphViewStyle().getLegendWidth();
    }

    /**
     * returns the maximal X value of the current viewport (if viewport is set)
     * otherwise maximal X value of all data.
     *
     * @param ignoreViewport warning: only override this, if you really know want you're doing!
     */
    private double getMaxX(boolean ignoreViewport) {
        // if viewport is set, use this
        if (!ignoreViewport && viewportSize != 0) {
            return viewportStart + viewportSize;
        } else {
            // otherwise use the max x value
            // values must be sorted by x, so the last value has the largest X value
            double highest = 0;
            if (graphSeries.size() > 0) {
                GraphViewDataInterface[] values = graphSeries.get(0).values;
                if (values.length == 0) {
                    highest = 0;
                } else {
                    highest = values[values.length - 1].getX();
                }
                for (int i = 1; i < graphSeries.size(); i++) {
                    values = graphSeries.get(i).values;
                    if (values.length > 0) {
                        highest = Math.max(highest, values[values.length - 1].getX());
                    }
                }
            }
            return highest;
        }
    }

    /**
     * returns the middle  X value of the current viewport (if viewport is set)
     * <p/>
     * warning: only override this, if you really know want you're doing!
     */
    public double getMiddleX() {
        return (int) (viewportStart + viewportSize / 2);
    }

    /**
     * returns the maximal Y value of all data.
     * <p/>
     * warning: only override this, if you really know want you're doing!
     */
    protected double getMaxY() {
        double largest;
        if (manualYAxis || manualMaxY) {
            largest = manualMaxYValue;
        } else {
            largest = Integer.MIN_VALUE;
            for (int i = 0; i < graphSeries.size(); i++) {
                GraphViewDataInterface[] values = _values(i);
                for (int ii = 0; ii < values.length; ii++)
                    if (values[ii].getY() > largest)
                        largest = values[ii].getY();
            }
        }
        return largest;
    }

    /**
     * returns the minimal X value of the current viewport (if viewport is set)
     * otherwise minimal X value of all data.
     *
     * @param ignoreViewport warning: only override this, if you really know want you're doing!
     */
    protected double getMinX(boolean ignoreViewport) {
        // if viewport is set, use this
        if (!ignoreViewport && viewportSize != 0) {
            return viewportStart;
        } else {
            // otherwise use the min x value
            // values must be sorted by x, so the first value has the smallest X value
            double lowest = 0;
            if (graphSeries.size() > 0) {
                GraphViewDataInterface[] values = graphSeries.get(0).values;
                if (values.length == 0) {
                    lowest = 0;
                } else {
                    lowest = values[0].getX();
                }
                for (int i = 1; i < graphSeries.size(); i++) {
                    values = graphSeries.get(i).values;
                    if (values.length > 0) {
                        lowest = Math.min(lowest, values[0].getX());
                    }
                }
            }
            return lowest;
        }
    }

    /**
     * returns the minimal Y value of all data.
     * <p/>
     * warning: only override this, if you really know want you're doing!
     */
    protected double getMinY() {
        double smallest;
        if (manualYAxis || manualMinY) {
            smallest = manualMinYValue;
        } else {
            smallest = Integer.MAX_VALUE;
            for (int i = 0; i < graphSeries.size(); i++) {
                GraphViewDataInterface[] values = _values(i);
                for (int ii = 0; ii < values.length; ii++)
                    if (values[ii].getY() < smallest)
                        smallest = values[ii].getY();
            }
        }
        return smallest;
    }

    /**
     * returns the size of the Viewport
     */
    public double getViewportSize() {
        return viewportSize;
    }

    public boolean isDisableTouch() {
        return disableTouch;
    }

    public boolean isScrollable() {
        return scrollable;
    }

    public boolean isShowLegend() {
        return showLegend;
    }

    /**
     * forces graphview to invalide all views and caches.
     * Normally there is no need to call this manually.
     */
    public void redrawAll() {
        if (!staticVerticalLabels) verlabels = null;
        if (!staticHorizontalLabels) horlabels = null;
        numberformatter[0] = null;
        numberformatter[1] = null;
        labelTextHeight = null;
        horLabelTextWidth = null;
        verLabelTextWidth = null;

        invalidate();
        viewVerLabels.invalidate();
        graphViewContentView.invalidate();
    }

    /**
     * removes all series
     */
    public void removeAllSeries() {
        for (GraphViewSeries s : graphSeries) {
            s.removeGraphView(this);
        }
        while (!graphSeries.isEmpty()) {
            graphSeries.remove(0);
        }
        redrawAll();
    }

    /**
     * removes a series
     *
     * @param series series to remove
     */
    public void removeSeries(GraphViewSeries series) {
        series.removeGraphView(this);
        graphSeries.remove(series);
        redrawAll();
    }

    /**
     * removes series
     *
     * @param index
     */
    public void removeSeries(int index) {
        if (index < 0 || index >= graphSeries.size()) {
            throw new IndexOutOfBoundsException("No series at index " + index);
        }

        removeSeries(graphSeries.get(index));
    }

    /**
     * scrolls to the last x-value
     *
     * @throws IllegalStateException if scrollable == false
     */
    public void scrollToEnd() {
        if (!scrollable) throw new IllegalStateException("This GraphView is not scrollable.");
        double max = getMaxX(true);
        viewportStart = max - viewportSize;

        // don't clear labels width/height cache
        // so that the display is not flickering
        if (!staticVerticalLabels) verlabels = null;
        if (!staticHorizontalLabels) horlabels = null;

        invalidate();
        viewVerLabels.invalidate();
        graphViewContentView.invalidate();
    }

    /**
     * set a custom label formatter
     *
     * @param customLabelFormatter
     */
    public void setCustomLabelFormatter(CustomLabelFormatter customLabelFormatter) {
        this.customLabelFormatter = customLabelFormatter;
    }

    /**
     * The user can disable any touch gestures, this is useful if you are using a real time graph, but don't want the user to interact
     *
     * @param disableTouch
     */
    public void setDisableTouch(boolean disableTouch) {
        this.disableTouch = disableTouch;
    }

    /**
     * set custom graphview style
     *
     * @param style
     */
    public void setGraphViewStyle(GraphViewStyle style) {
        graphViewStyle = style;
        labelTextHeight = null;
    }

    /**
     * set's static horizontal labels (from left to right)
     *
     * @param horlabels if null, labels were generated automatically
     */
    public void setHorizontalLabels(String[] horlabels) {
        staticHorizontalLabels = horlabels != null;
        this.horlabels = horlabels;
    }

    /**
     * legend position
     *
     * @param legendAlign
     */
    public void setLegendAlign(LegendAlign legendAlign) {
        this.legendAlign = legendAlign;
    }

    /**
     * legend width
     *
     * @param legendWidth
     * @deprecated use {@link GraphViewStyle#setLegendWidth(int)}
     */
    @Deprecated
    public void setLegendWidth(float legendWidth) {
        getGraphViewStyle().setLegendWidth((int) legendWidth);
    }

    /**
     * you have to set the bounds {@link #setManualYAxisBounds(double, double)}. That automatically enables manualYAxis-flag.
     * if you want to disable the menual y axis, call this method with false.
     *
     * @param manualYAxis
     */
    public void setManualYAxis(boolean manualYAxis) {
        this.manualYAxis = manualYAxis;
    }

    /**
     * if you want to disable the menual y axis maximum bound, call this method with false.
     */
    public void setManualMaxY(boolean manualMaxY) {
        this.manualMaxY = manualMaxY;
    }

    /**
     * if you want to disable the menual y axis minimum bound, call this method with false.
     */
    public void setManualMinY(boolean manualMinY) {
        this.manualMinY = manualMinY;
    }

    /**
     * set manual Y axis limit
     *
     * @param max
     * @param min
     */
    public void setManualYAxisBounds(double max, double min) {
        manualMaxYValue = max;
        manualMinYValue = min;
        manualYAxis = true;
    }

    /*
     * set manual Y axis max limit
     * @param max
     */
    public void setManualYMaxBound(double max) {
        manualMaxYValue = max;
        manualMaxY = true;
    }

    /*
     * set manual Y axis min limit
     * @param min
     */
    public void setManualYMinBound(double min) {
        manualMinYValue = min;
        manualMinY = true;
    }

    /**
     * this forces scrollable = true
     *
     * @param scalable
     */
    synchronized public void setScalable(boolean scalable) {
        this.scalable = scalable;
    }

    /**
     * the user can scroll (horizontal) the graph. This is only useful if you use a viewport {@link #setViewPort(double, double)} which doesn't displays all data.
     *
     * @param scrollable
     */
    public void setScrollable(boolean scrollable) {
        this.scrollable = scrollable;
    }

    public void setShowLegend(boolean showLegend) {
        this.showLegend = showLegend;
    }

    /**
     * sets the title of graphview
     *
     * @param title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * set's static vertical labels (from top to bottom)
     *
     * @param verlabels if null, labels were generated automatically
     */
    public void setVerticalLabels(String[] verlabels) {
        staticVerticalLabels = verlabels != null;
        this.verlabels = verlabels;
    }

    /**
     * set's the viewport for the graph.
     *
     * @param start x-value
     * @param size
     * @see #setManualYAxisBounds(double, double) to limit the y-viewport
     */
    public void setViewPort(double start, double size) {
        if (size < 0) {
            throw new IllegalArgumentException("Viewport size must be greater than 0!");
        }
        viewportStart = start;
        viewportSize = size;
    }

    /**
     * Sets whether horizontal labels are drawn or not.
     *
     * @param showHorizontalLabels
     */
    public void setShowHorizontalLabels(boolean showHorizontalLabels) {
        this.showHorizontalLabels = showHorizontalLabels;
        redrawAll();
    }

    /**
     * Gets are horizontal labels drawn.
     *
     * @return {@code True} if horizontal labels are drawn
     */
    public boolean getShowHorizontalLabels() {
        return showHorizontalLabels;
    }

    /**
     * Sets whether vertical labels are drawn or not.
     *
     * @param showVerticalLabels
     */
    public void setShowVerticalLabels(boolean showVerticalLabels) {
        this.showVerticalLabels = showVerticalLabels;
        if (this.showVerticalLabels) {
            addView(viewVerLabels, 0);
        } else {
            removeView(viewVerLabels);
        }
    }

    /**
     * Gets are vertical labels are drawn.
     *
     * @return {@code True} if vertical labels are drawn
     */
    public boolean getShowVerticalLabels() {
        return showVerticalLabels;
    }

}
