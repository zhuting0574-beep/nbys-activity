package com.nbys.training

import android.graphics.Rect
import android.media.Image

data class LaserHit(val x: Float, val y: Float, val confidence: Float)
data class CalibrationPoint(val x: Float, val y: Float)
data class CalibrationResult(val points: List<CalibrationPoint>) { val complete get() = points.size == 4 }

/** Finds the four proprietary black/white/black registration marks near the target corners. */
object CalibrationDetector {
    @Volatile var diagnostics="等待分析"
        private set
    @Volatile var locked=false
        private set
    private val stableSamples=ArrayDeque<List<CalibrationPoint>>()
    private const val workWidth=270
    private const val workHeight=480
    private val gray=IntArray(workWidth*workHeight)
    private val grayFeature=IntArray(workWidth*workHeight)
    private val grayIntegral=IntArray((workWidth+1)*(workHeight+1))
    private val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
    private val busy=java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var latestResult=CalibrationResult(emptyList())
    @Volatile private var generation=0

    private data class PatternCandidate(val x:Int,val y:Int,val score:Double,val contrast:Double,val ringEvidence:Double,val offset:Int)

    fun reset() { generation++;stableSamples.clear();locked=false;latestResult=CalibrationResult(emptyList());diagnostics="等待分析" }

    fun detect(image:Image,crop:Rect):CalibrationResult {
        if(!busy.compareAndSet(false,true)) return latestResult
        buildWorkingImage(image,crop)
        val imageWidth=image.width;val imageHeight=image.height;val cropCopy=Rect(crop);val requestGeneration=generation
        worker.execute {
            try {
                val result=processWorkingImage(imageWidth,imageHeight,cropCopy)
                if(requestGeneration==generation)latestResult=result
            } finally { busy.set(false) }
        }
        return latestResult
    }

    private fun processWorkingImage(imageWidth:Int,imageHeight:Int,crop:Rect):CalibrationResult {
        val reference=if(stableSamples.size>=2)robustAverage(stableSamples.toList()) else emptyList()
        val rois=arrayOf(
            intArrayOf(2,2,workWidth/2,workHeight/2),intArrayOf(workWidth/2,2,workWidth-2,workHeight/2),
            intArrayOf(workWidth/2,workHeight/2,workWidth-2,workHeight-2),intArrayOf(2,workHeight/2,workWidth/2,workHeight-2))
        val candidateGroups=ArrayList<List<PatternCandidate>>(4)
        for(corner in 0 until 4) {
            val ref=reference.getOrNull(corner)
            val roi=if(ref==null)rois[corner] else {
                val wx=((1f-ref.y)*workWidth).toInt();val wy=(ref.x*workHeight).toInt();val r=22
                intArrayOf((wx-r).coerceAtLeast(2),(wy-r).coerceAtLeast(2),(wx+r).coerceAtMost(workWidth-2),(wy+r).coerceAtMost(workHeight-2))
            }
            val candidates=findPatterns(roi,ref!=null)
            if(candidates.isEmpty()) { if(reference.isNotEmpty())stableSamples.clear();locked=false;diagnostics="黑白方标：第 ${corner+1} 个定位标记未找到";return CalibrationResult(emptyList()) }
            candidateGroups+=candidates
        }
        val found=selectCandidateGroup(candidateGroups) ?: run { stableSamples.clear();locked=false;diagnostics="黑白方标：未找到几何关系有效的四点组合";return CalibrationResult(emptyList()) }
        val scaleRatio=found.maxOf { it.offset }.toDouble()/found.minOf { it.offset }.coerceAtLeast(1)
        val strong=found.count { it.contrast>=26.0&&it.score>=78.0&&it.ringEvidence>=18.0 }
        val geometryValid=validMarkerGeometry(found)
        if(found.any { it.contrast<14.0||it.score<45.0||it.ringEvidence<12.0 }||strong<3||scaleRatio>2.6||!geometryValid) {
            stableSamples.clear();locked=false
            diagnostics="黑白方标：三级环校验未通过 · 强标记 $strong/4 · 尺寸比 %.2f".format(scaleRatio)
            return CalibrationResult(emptyList())
        }
        val points=found.map { candidate ->
            val refined=refineCenter(candidate)
            val rawX=crop.left+refined.second/workHeight*crop.width()
            val rawY=crop.top+(1f-refined.first/workWidth)*crop.height()
            CalibrationPoint(rawX/imageWidth,rawY/imageHeight)
        }.let { orderCorners(it) }
        val currentReference=robustAverage(stableSamples.toList())
        val jump=if(currentReference.size==4)points.indices.maxOf { i -> kotlin.math.hypot(((points[i].x-currentReference[i].x)*imageWidth).toDouble(),((points[i].y-currentReference[i].y)*imageHeight).toDouble()) } else 0.0
        if(currentReference.isEmpty()||jump<=8.0) { stableSamples.addLast(points);while(stableSamples.size>12)stableSamples.removeFirst() } else stableSamples.clear()
        locked=stableSamples.size>=8
        val filtered=if(stableSamples.size>=2)robustAverage(stableSamples.toList()) else points
        diagnostics="黑白方标：4/4 · 强标记 $strong/4 · 稳定 ${stableSamples.size}/8"
        return CalibrationResult(filtered)
    }

    private fun buildWorkingImage(image:Image,crop:Rect) {
        val yp=image.planes[0]
        for(wy in 0 until workHeight) {
            val rawX=crop.left+(wy*crop.width()/workHeight).coerceIn(0,crop.width()-1)
            for(wx in 0 until workWidth) {
                val rawY=crop.top+crop.height()-1-(wx*crop.height()/workWidth).coerceIn(0,crop.height()-1)
                val yy=sample(yp,rawX,rawY);val index=wy*workWidth+wx
                gray[index]=yy;grayFeature[index]=yy
            }
        }
        buildIntegral(grayFeature,grayIntegral)
    }

    private fun buildIntegral(source:IntArray,target:IntArray) {
        java.util.Arrays.fill(target,0)
        val stride=workWidth+1
        for(y in 1..workHeight){var row=0;for(x in 1..workWidth){row+=source[(y-1)*workWidth+x-1];target[y*stride+x]=target[(y-1)*stride+x]+row}}
    }

    private fun patchMean(integral:IntArray,x:Int,y:Int,r:Int=1):Int {
        val x0=x-r;val y0=y-r;val x1=x+r+1;val y1=y+r+1
        if(x0<0||y0<0||x1>workWidth||y1>workHeight)return -1
        val stride=workWidth+1;return (integral[y1*stride+x1]-integral[y0*stride+x1]-integral[y1*stride+x0]+integral[y0*stride+x0])/((r*2+1)*(r*2+1))
    }

    private fun findPatterns(roi:IntArray,tracking:Boolean):List<PatternCandidate> {
        val coarseByCell=HashMap<String,PatternCandidate>();val offsets=intArrayOf(4,5,6,7,8,10,12,14,16,18);val step=if(tracking)2 else 3
        var y=roi[1]+7
        while(y<roi[3]-7){var x=roi[0]+7;while(x<roi[2]-7){for(d in offsets){val c=patternScore(x,y,d,false);if(c!=null){val key="${x/9}:${y/9}";val old=coarseByCell[key];if(old==null||c.score>old.score)coarseByCell[key]=c}};x+=step};y+=step}
        return coarseByCell.values.sortedByDescending { it.score }.take(6).map { coarse ->
            var refined=coarse
            for(yy in coarse.y-4..coarse.y+4)for(xx in coarse.x-4..coarse.x+4)for(d in (coarse.offset-3).coerceAtLeast(3)..(coarse.offset+3).coerceAtMost(21)){val c=patternScore(xx,yy,d,true);if(c!=null&&c.score>refined.score)refined=c}
            refined
        }.distinctBy { "${it.x/5}:${it.y/5}:${it.offset/2}" }.take(5)
    }

    private fun selectCandidateGroup(groups:List<List<PatternCandidate>>):List<PatternCandidate>? {
        if(groups.size!=4)return null
        var best:List<PatternCandidate>?=null;var bestScore=Double.NEGATIVE_INFINITY
        for(a in groups[0])for(b in groups[1])for(c in groups[2])for(d in groups[3]) {
            val points=listOf(a,b,c,d);val minSize=points.minOf { it.offset }.coerceAtLeast(1);val scale=points.maxOf { it.offset }.toDouble()/minSize
            if(scale>2.6||!validMarkerGeometry(points))continue
            val strong=points.count { it.contrast>=26.0&&it.score>=78.0&&it.ringEvidence>=18.0 }
            if(strong<3||points.any { it.ringEvidence<12.0 })continue
            val score=points.sumOf { it.score }-22.0*(scale-1.0)
            if(score>bestScore){bestScore=score;best=points}
        }
        return best
    }

    private fun patternScore(cx:Int,cy:Int,d:Int,refined:Boolean):PatternCandidate? {
        val coreRadius=(d*.27).toInt().coerceAtLeast(1)
        val core=patchMean(grayIntegral,cx,cy,coreRadius)
        val whiteRing=ringMean(cx,cy,(d*.38).toInt().coerceAtLeast(coreRadius+1),(d*.66).toInt().coerceAtLeast(coreRadius+2))
        val blackRing=ringMean(cx,cy,(d*.73).toInt().coerceAtLeast(2),d)
        val outside=ringMean(cx,cy,(d*1.08).toInt(),(d*1.34).toInt())
        if(core<0||whiteRing<0||blackRing<0||outside<0)return null
        val centerGap=whiteRing-core
        val ringGap=whiteRing-blackRing
        val edgeGap=outside-blackRing
        val contrast=minOf(centerGap,ringGap,edgeGap).toDouble()
        if(contrast<(if(refined)10 else 13)||whiteRing<62||maxOf(core,blackRing)>220)return null
        val darkMismatch=kotlin.math.abs(core-blackRing)
        val lightMismatch=kotlin.math.abs(whiteRing-outside)
        val score=centerGap+ringGap+edgeGap-darkMismatch*.22-lightMismatch*.16
        return PatternCandidate(cx,cy,score,contrast,minOf(centerGap,ringGap).toDouble(),d)
    }

    private fun ringMean(cx:Int,cy:Int,innerRadius:Int,outerRadius:Int):Int {
        if(outerRadius<=innerRadius)return -1
        val outer=rectSum(grayIntegral,cx-outerRadius,cy-outerRadius,cx+outerRadius+1,cy+outerRadius+1)
        val inner=rectSum(grayIntegral,cx-innerRadius,cy-innerRadius,cx+innerRadius+1,cy+innerRadius+1)
        if(outer<0||inner<0)return -1
        val outerArea=(outerRadius*2+1)*(outerRadius*2+1)
        val innerArea=(innerRadius*2+1)*(innerRadius*2+1)
        return ((outer-inner)/(outerArea-innerArea).coerceAtLeast(1)).toInt()
    }

    private fun rectSum(integral:IntArray,x0:Int,y0:Int,x1:Int,y1:Int):Long {
        if(x0<0||y0<0||x1>workWidth||y1>workHeight||x1<=x0||y1<=y0)return -1
        val stride=workWidth+1
        return (integral[y1*stride+x1]-integral[y0*stride+x1]-integral[y1*stride+x0]+integral[y0*stride+x0]).toLong()
    }

    private fun validMarkerGeometry(points:List<PatternCandidate>):Boolean {
        if(points.size!=4)return false
        fun distance(a:PatternCandidate,b:PatternCandidate)=kotlin.math.hypot((a.x-b.x).toDouble(),(a.y-b.y).toDouble())
        val top=distance(points[0],points[1]);val bottom=distance(points[3],points[2]);val left=distance(points[0],points[3]);val right=distance(points[1],points[2])
        if(minOf(top,bottom)<workWidth*.12||minOf(left,right)<workHeight*.12)return false
        val widthBalance=minOf(top,bottom)/maxOf(top,bottom).coerceAtLeast(1.0)
        val heightBalance=minOf(left,right)/maxOf(left,right).coerceAtLeast(1.0)
        val area=kotlin.math.abs(points.indices.sumOf { i -> val a=points[i];val b=points[(i+1)%4];a.x.toDouble()*b.y-b.x.toDouble()*a.y })/2.0
        val xs=points.map { it.x };val ys=points.map { it.y };val box=(xs.max()-xs.min()).toDouble()*(ys.max()-ys.min()).toDouble()
        return widthBalance>=.35&&heightBalance>=.35&&box>0&&area/box>=.48
    }

    private fun refineCenter(candidate:PatternCandidate):Pair<Float,Float> {
        val radius=(candidate.offset*.38).toInt().coerceIn(2,10);val values=ArrayList<Int>()
        for(y in (candidate.y-radius).coerceAtLeast(0)..(candidate.y+radius).coerceAtMost(workHeight-1))for(x in (candidate.x-radius).coerceAtLeast(0)..(candidate.x+radius).coerceAtMost(workWidth-1))if((x-candidate.x)*(x-candidate.x)+(y-candidate.y)*(y-candidate.y)<=radius*radius)values+=gray[y*workWidth+x]
        if(values.size<24)return Pair(candidate.x+.5f,candidate.y+.5f);values.sort();val low=values[(values.size*.1).toInt()];val high=values[(values.size*.9).toInt().coerceAtMost(values.lastIndex)];val threshold=low+(high-low)*.55
        var weight=0.0;var sx=0.0;var sy=0.0
        for(y in (candidate.y-radius).coerceAtLeast(0)..(candidate.y+radius).coerceAtMost(workHeight-1))for(x in (candidate.x-radius).coerceAtLeast(0)..(candidate.x+radius).coerceAtMost(workWidth-1)){if((x-candidate.x)*(x-candidate.x)+(y-candidate.y)*(y-candidate.y)>radius*radius)continue;val w=(threshold-gray[y*workWidth+x]).coerceAtLeast(0.0);weight+=w;sx+=(x+.5)*w;sy+=(y+.5)*w}
        if(weight<=0.0)return Pair(candidate.x+.5f,candidate.y+.5f);val rx=(sx/weight).toFloat();val ry=(sy/weight).toFloat();val dx=rx-(candidate.x+.5f);val dy=ry-(candidate.y+.5f);val shift=kotlin.math.hypot(dx.toDouble(),dy.toDouble()).toFloat();val scale=if(shift>1.5f)1.5f/shift else 1f
        return Pair(candidate.x+.5f+dx*scale,candidate.y+.5f+dy*scale)
    }

    private fun detectLegacy(image: Image, crop:Rect): CalibrationResult {
        val yPlane = image.planes[0]; val uPlane = image.planes[1]; val vPlane = image.planes[2]
        val width = crop.width(); val height = crop.height()
        // The printed marks can be only 8-12 pixels wide when the whole target is in view.
        val step = 3
        val gridWidth = (width + step - 1) / step; val gridHeight = (height + step - 1) / step
        val yellow = BooleanArray(gridWidth * gridHeight)
        val black = BooleanArray(gridWidth * gridHeight)
        for (gy in 0 until gridHeight) for (gx in 0 until gridWidth) {
            val x = crop.left + gx * step; val y = crop.top + gy * step
            val yy = sample(yPlane, x, y); val uu = sample(uPlane, x / 2, y / 2); val vv = sample(vPlane, x / 2, y / 2)
            // Yellow registration marks remain saturated even under warm indoor light.
            yellow[gy * gridWidth + gx] = yy > 58 && uu < 150 && vv in 105..225 && vv-uu>8
            black[gy * gridWidth + gx] = yy < 125 && kotlin.math.abs(uu - 128) < 70 && kotlin.math.abs(vv - 128) < 70
        }
        data class Blob(val count:Int,val x:Float,val y:Float,val width:Int,val height:Int) {
            val fill get()=count.toFloat()/(width*height).coerceAtLeast(1)
            val compact get()=minOf(width,height).toFloat()/maxOf(width,height).coerceAtLeast(1)
        }
        val seen = BooleanArray(yellow.size); val blobs = mutableListOf<Blob>()
        for (start in yellow.indices) {
            if (!yellow[start] || seen[start]) continue
            val queue = IntArray(yellow.size); var head = 0; var tail = 0; queue[tail++] = start; seen[start] = true
            var count = 0; var sumX = 0L; var sumY = 0L; var minX=gridWidth; var maxX=0; var minY=gridHeight; var maxY=0
            while (head < tail) {
                val cell = queue[head++]; val gx = cell % gridWidth; val gy = cell / gridWidth
                count++; sumX += gx; sumY += gy; minX=minOf(minX,gx); maxX=maxOf(maxX,gx); minY=minOf(minY,gy); maxY=maxOf(maxY,gy)
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = gx + dx; val ny = gy + dy
                    if (nx !in 0 until gridWidth || ny !in 0 until gridHeight) continue
                    val next = ny * gridWidth + nx
                    if (yellow[next] && !seen[next]) { seen[next] = true; queue[tail++] = next }
                }
            }
            if (count >= 1) blobs += Blob(count, (sumX.toFloat() / count * step) / width, (sumY.toFloat() / count * step) / height,maxX-minX+1,maxY-minY+1)
        }
        // One yellow quarter is a small compact patch. Long highlights and cabinet edges are rejected here.
        val rawCandidates = blobs.filter { it.count in 1..600 && it.width<=32 && it.height<=32 && it.compact>=.25f && it.fill>=.18f }.sortedByDescending { it.count }.take(100)
        data class Marker(val point:CalibrationPoint,val radius:Int,val score:Float)
        val markers=mutableListOf<Marker>()
        for(i in 0 until rawCandidates.size-1) for(j in i+1 until rawCandidates.size) {
            val a=rawCandidates[i]; val b=rawCandidates[j]
            val dx=(a.x-b.x)*gridWidth; val dy=(a.y-b.y)*gridHeight
            val distance=kotlin.math.hypot(dx.toDouble(),dy.toDouble()).toFloat()
            if(distance !in 1.2f..24.0f) continue
            val diagonalRatio=minOf(kotlin.math.abs(dx),kotlin.math.abs(dy))/maxOf(kotlin.math.abs(dx),kotlin.math.abs(dy)).coerceAtLeast(.001f)
            if(diagonalRatio<.18f) continue
            val sizeRatio=minOf(a.count,b.count).toFloat()/maxOf(a.count,b.count).coerceAtLeast(1)
            if(sizeRatio<.12f) continue
            val point=CalibrationPoint((a.x+b.x)/2f,(a.y+b.y)/2f)
            val radius=(distance*.9f).toInt().coerceIn(3,18)
            val score=yellowBlackScore(point,radius,yellow,black,gridWidth,gridHeight)
            if(score>=.34f) markers += Marker(point,radius,score+sizeRatio*.15f)
        }
        val candidates=markers.sortedByDescending { it.score }.fold(mutableListOf<CalibrationPoint>()) { accepted,marker ->
            if(accepted.none { kotlin.math.hypot(((it.x-marker.point.x)*gridWidth).toDouble(),((it.y-marker.point.y)*gridHeight).toDouble()) < marker.radius }) accepted += marker.point
            accepted
        }.take(12)
        val selected = bestFour(candidates)
        val points = if (selected.size == 4) orderCorners(selected) else selected
        if(points.size==4) {
            val reference=robustAverage(stableSamples.toList())
            val jump=if(reference.size==4) points.indices.maxOf { i ->
                kotlin.math.hypot(((points[i].x-reference[i].x)*width).toDouble(),((points[i].y-reference[i].y)*height).toDouble())
            } else 0.0
            if(reference.isEmpty()||jump<=8.0) {
                stableSamples.addLast(points)
                while(stableSamples.size>12) stableSamples.removeFirst()
            } else stableSamples.clear()
        }
        val filtered=if(stableSamples.size>=8) robustAverage(stableSamples.toList()) else points
        diagnostics="黄色块 ${blobs.size} · 有效色块 ${rawCandidates.size} · 候选 ${candidates.size} · 稳定 ${stableSamples.size}/8"
        return CalibrationResult(filtered)
    }

    private fun robustAverage(samples:List<List<CalibrationPoint>>):List<CalibrationPoint> {
        if(samples.isEmpty()||samples.any { it.size!=4 }) return emptyList()
        return (0 until 4).map { corner ->
            val xs=samples.map { it[corner].x }.sorted();val ys=samples.map { it[corner].y }.sorted()
            val medianX=xs[xs.size/2];val medianY=ys[ys.size/2]
            val accepted=samples.map { it[corner] }.filter { kotlin.math.hypot((it.x-medianX).toDouble(),(it.y-medianY).toDouble())<.02 }
            val effective=if(accepted.size>=4)accepted else samples.map { it[corner] }
            CalibrationPoint(effective.map { it.x }.average().toFloat(),effective.map { it.y }.average().toFloat())
        }
    }

    /** A registration mark is a compact circle with alternating yellow and black sectors. */
    private fun yellowBlackScore(point:CalibrationPoint,radius:Int,yellow:BooleanArray,black:BooleanArray,w:Int,h:Int):Float {
        val cx=(point.x*w).toInt(); val cy=(point.y*h).toInt()
        var best=0f
        for(r in (radius-2).coerceAtLeast(2)..(radius+2)) {
            if(cx-r<0 || cy-r<0 || cx+r>=w || cy+r>=h) continue
            val yellowByQuadrant=IntArray(4); val blackByQuadrant=IntArray(4); val samples=IntArray(4)
            for(dy in -r..r) for(dx in -r..r) {
                val distance=dx*dx+dy*dy
                if(distance>r*r) continue
                val quadrant=if(dy<0) { if(dx<0) 0 else 1 } else { if(dx>=0) 2 else 3 }
                val index=(cy+dy)*w+cx+dx
                samples[quadrant]++
                if(yellow[index]) yellowByQuadrant[quadrant]++
                if(black[index]) blackByQuadrant[quadrant]++
            }
            val yellowRatio=yellowByQuadrant.sum().toFloat()/samples.sum().coerceAtLeast(1)
            val blackRatio=blackByQuadrant.sum().toFloat()/samples.sum().coerceAtLeast(1)
            if(yellowRatio<.045f || blackRatio<.06f || yellowRatio+blackRatio<.20f) continue
            fun sectorRatio(values:IntArray,q:Int)=values[q].toFloat()/samples[q].coerceAtLeast(1)
            // Accept either diagonal color assignment. Perspective may distort sectors, so score all four rather than requiring perfect quarters.
            val patternA=(sectorRatio(yellowByQuadrant,0)+sectorRatio(blackByQuadrant,1)+sectorRatio(yellowByQuadrant,2)+sectorRatio(blackByQuadrant,3))/4f
            val patternB=(sectorRatio(blackByQuadrant,0)+sectorRatio(yellowByQuadrant,1)+sectorRatio(blackByQuadrant,2)+sectorRatio(yellowByQuadrant,3))/4f
            best=maxOf(best,maxOf(patternA,patternB))
        }
        return best
    }

    private fun bestFour(points:List<CalibrationPoint>):List<CalibrationPoint> {
        if(points.size<4) return points
        var best=emptyList<CalibrationPoint>(); var bestScore=0.0
        for(a in 0 until points.size-3) for(b in a+1 until points.size-2) for(c in b+1 until points.size-1) for(d in c+1 until points.size) {
            val ordered=orderCorners(listOf(points[a],points[b],points[c],points[d])); val area=polygonArea(ordered)
            val xs=ordered.map { it.x }; val ys=ordered.map { it.y }; val box=(xs.max()-xs.min())*(ys.max()-ys.min())
            if(area<.004 || box<=0f) continue
            val fill=area/box; val score=area*fill*fill
            val top=ordered.sortedBy { it.y }.take(2); val bottom=ordered.sortedByDescending { it.y }.take(2)
            val left=ordered.sortedBy { it.x }.take(2); val right=ordered.sortedByDescending { it.x }.take(2)
            val verticalSeparation=bottom.map { it.y }.average()-top.map { it.y }.average()
            val horizontalSeparation=right.map { it.x }.average()-left.map { it.x }.average()
            val topLevel=kotlin.math.abs(top[0].y-top[1].y); val bottomLevel=kotlin.math.abs(bottom[0].y-bottom[1].y)
            if(fill>.42 && verticalSeparation>.05 && horizontalSeparation>.05 && topLevel<verticalSeparation*.70 && bottomLevel<verticalSeparation*.70 && score>bestScore) { bestScore=score; best=ordered }
        }
        return best
    }

    private fun polygonArea(points:List<CalibrationPoint>):Double { var sum=0.0; for(i in points.indices){ val next=points[(i+1)%points.size]; sum+=points[i].x*next.y-next.x*points[i].y }; return kotlin.math.abs(sum)/2 }

    private fun orderCorners(points:List<CalibrationPoint>):List<CalibrationPoint> {
        val centerX = points.sumOf { it.x.toDouble() }.toFloat() / points.size; val centerY = points.sumOf { it.y.toDouble() }.toFloat() / points.size
        return points.sortedBy { kotlin.math.atan2((it.y - centerY).toDouble(), (it.x - centerX).toDouble()) }.let { ordered ->
            val first = ordered.indices.minByOrNull { ordered[it].x + ordered[it].y } ?: 0
            ordered.drop(first) + ordered.take(first)
        }
    }

    private fun sample(plane: android.media.Image.Plane, x: Int, y: Int): Int {
        val index = y * plane.rowStride + x * plane.pixelStride
        return if (index in 0 until plane.buffer.limit()) plane.buffer.get(index).toInt() and 0xff else 0
    }
}

/** Detects the brightest red region. Calibration maps this normalized frame point to the target. */
object LaserDetector {
    private data class Probe(val score:Int,val x:Int,val y:Int)
    private val backgroundScores=ArrayDeque<Int>()
    private var threshold=0
    private var hysteresis=12
    private var laserActive=false
    private var lastHitAt=0L

    fun resetPulse() { backgroundScores.clear();threshold=0;hysteresis=12;laserActive=false;lastHitAt=0L }

    fun updateBackground(image:Image,crop:Rect) {
        backgroundScores.addLast(fastProbe(image,crop).score)
        while(backgroundScores.size>180) backgroundScores.removeFirst()
        if(backgroundScores.size>=20) {
            val sorted=backgroundScores.sorted()
            fun percentile(f:Double)=sorted[((sorted.size-1)*f).toInt().coerceIn(0,sorted.size-1)]
            val median=percentile(.50);val p95=percentile(.95);val p99=percentile(.99)
            val spread=maxOf(4,p99-median);val highBackground=p99>=395
            val margin=if(highBackground) (3+(p99-p95)/12).coerceIn(3,6) else maxOf(20,(spread*1.5).toInt()).coerceAtMost(45)
            threshold=p99+margin
            hysteresis=if(highBackground) maxOf(5,margin+2) else maxOf(12,margin/2)
        }
    }

    fun detect(image:Image,crop:Rect):LaserHit? {
        if(threshold<=0) { updateBackground(image,crop);return null }
        val probe=fastProbe(image,crop)
        if(probe.score<threshold-hysteresis) laserActive=false
        if(probe.score<threshold||laserActive||probe.x<0) return null
        val now=android.os.SystemClock.elapsedRealtime()
        if(now-lastHitAt<70) return null
        val refined=refineRedComponent(image,probe.x,probe.y) ?: return null
        laserActive=true;lastHitAt=now
        return LaserHit(refined.first/image.width,refined.second/image.height,(probe.score/640f).coerceIn(0f,1f))
    }

    private fun fastProbe(image:Image,crop:Rect):Probe {
        val yPlane=image.planes[0];val vPlane=image.planes[2]
        val bestV=IntArray(8){-1};val bestX=IntArray(8){-1};val bestY=IntArray(8){-1}
        val border=24
        var y=maxOf(crop.top+border,0)
        while(y<minOf(crop.bottom-border,image.height)) {
            var x=maxOf(crop.left+border,0)
            while(x<minOf(crop.right-border,image.width)) {
                val v=sample(vPlane,x/2,y/2)
                if(v>bestV[7]) { var pos=7;while(pos>0&&v>bestV[pos-1]){bestV[pos]=bestV[pos-1];bestX[pos]=bestX[pos-1];bestY[pos]=bestY[pos-1];pos--};bestV[pos]=v;bestX[pos]=x;bestY[pos]=y }
                x+=4
            }
            y+=4
        }
        var result=Probe(0,-1,-1)
        for(i in bestV.indices) if(bestX[i]>=0) { val score=(bestV[i] shl 1)+(sample(yPlane,bestX[i],bestY[i]) shr 1);if(score>result.score)result=Probe(score,bestX[i],bestY[i]) }
        return result
    }

    private fun refineRedComponent(image:Image,centerX:Int,centerY:Int):Pair<Float,Float>? {
        val yPlane=image.planes[0];val uPlane=image.planes[1];val vPlane=image.planes[2]
        val radius=40;val minX=(centerX-radius).coerceAtLeast(0);val maxX=(centerX+radius).coerceAtMost(image.width-1);val minY=(centerY-radius).coerceAtLeast(0);val maxY=(centerY+radius).coerceAtMost(image.height-1)
        val width=maxX-minX+1;val height=maxY-minY+1;val scores=IntArray(width*height){Int.MIN_VALUE};var best=Int.MIN_VALUE;var bestIndex=-1
        for(y in minY..maxY) for(x in minX..maxX) {
            val yy=sample(yPlane,x,y);val uu=sample(uPlane,x/2,y/2);val vv=sample(vPlane,x/2,y/2);val c=yy-16;val d=uu-128;val e=vv-128
            val r=((298*c+409*e+128) shr 8).coerceIn(0,255);val g=((298*c-100*d-208*e+128) shr 8).coerceIn(0,255);val b=((298*c+516*d+128) shr 8).coerceIn(0,255)
            val score=(r-g)+(r-b)+r/4;val index=(y-minY)*width+x-minX;scores[index]=score;if(score>best){best=score;bestIndex=index}
        }
        if(bestIndex<0||best<80)return null
        val clusterThreshold=maxOf(80,best-100,(best*.55).toInt());val queue=IntArray(width*height);val seen=BooleanArray(width*height);var head=0;var tail=0
        queue[tail++]=bestIndex;seen[bestIndex]=true
        var count=0;var weightSum=0.0;var xSum=0.0;var ySum=0.0
        while(head<tail){val index=queue[head++];val ly=index/width;val lx=index%width;val weight=(scores[index]-clusterThreshold+1).coerceAtLeast(1).toDouble();count++;weightSum+=weight;xSum+=(minX+lx+.5)*weight;ySum+=(minY+ly+.5)*weight
            for(dy in -1..1)for(dx in -1..1){if(dx==0&&dy==0)continue;val nx=lx+dx;val ny=ly+dy;if(nx !in 0 until width||ny !in 0 until height)continue;val next=ny*width+nx;if(!seen[next]&&scores[next]>=clusterThreshold){seen[next]=true;queue[tail++]=next}}
        }
        if(count<2||weightSum<=0.0)return null
        return Pair((xSum/weightSum).toFloat(),(ySum/weightSum).toFloat())
    }

    private fun sample(plane: android.media.Image.Plane, x:Int, y:Int):Int { val index=y*plane.rowStride+x*plane.pixelStride; return if(index in 0 until plane.buffer.limit()) plane.buffer.get(index).toInt() and 0xff else 0 }
}

/** Low-cost scene stability check used to freeze calibration only after three still frames. */
object SceneMonitor {
    private var previous=IntArray(0)
    private var anchor=IntArray(0)
    private var stillFrames=0

    fun reset() { previous=IntArray(0); anchor=IntArray(0); stillFrames=0 }

    fun observe(image:Image,crop:Rect):Boolean {
        val sample=signature(image,crop)
        if(previous.size!=sample.size) { previous=sample; stillFrames=0; return false }
        val changed=changeRatio(previous,sample,.10f)
        stillFrames=if(changed<.025f) stillFrames+1 else 0
        previous=sample
        return stillFrames>=3
    }

    fun saveAnchor(image:Image,crop:Rect) { anchor=signature(image,crop) }

    fun moved(image:Image,crop:Rect):Boolean {
        if(anchor.isEmpty()) return false
        return changeRatio(anchor,signature(image,crop),.18f)>.16f
    }

    private fun signature(image:Image,crop:Rect):IntArray {
        val plane=image.planes[0]; val step=24
        val width=(crop.width()+step-1)/step; val height=(crop.height()+step-1)/step
        return IntArray(width*height) { index ->
            val x=crop.left+(index%width)*step; val y=crop.top+(index/width)*step
            val offset=y*plane.rowStride+x*plane.pixelStride
            if(offset in 0 until plane.buffer.limit()) plane.buffer.get(offset).toInt() and 0xff else 0
        }
    }

    private fun changeRatio(a:IntArray,b:IntArray,relativeThreshold:Float):Float {
        if(a.size!=b.size || a.isEmpty()) return 1f
        var changed=0
        for(i in a.indices) {
            val threshold=maxOf(22f,a[i]*relativeThreshold)
            if(kotlin.math.abs(a[i]-b[i])>threshold) changed++
        }
        return changed.toFloat()/a.size
    }
}
