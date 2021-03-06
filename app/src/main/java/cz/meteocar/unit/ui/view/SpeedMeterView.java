package cz.meteocar.unit.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import cz.meteocar.unit.R;
import cz.meteocar.unit.engine.storage.DB;

public class SpeedMeterView extends View implements ValueAnimator.AnimatorUpdateListener {

    private boolean displayInThousands = false;     // má zobrazovat hodnoty v tísících?

    // hodnota
    private int targetValue;            // skutečná hodnota (z OBD)
    private int value;                  // aktuální hodnota na tachometru
    private int oldValue;               // minulá hodnota před změnou
    ValueAnimator animator;

    // druhá hodnota
    private boolean secValueEnabled = false;
    private int secValue = 0;

    // bitmapy
    private Bitmap bmpOffline;          // zhaslé kousky
    private Bitmap bmpOnline;           // rozsvícené kousky
    private Bitmap bmpOfflineMasked;    // zhaslá část po vymaskování
    private Bitmap bmpOnlineMasked;     // zozsvícená část po vymaskování

    // velikost layoutu
    private int width;
    private int height;
    private int tachoSize;              // min(width,height)

    // kanvasy a průhledný paint
    private Paint pTrans;           // paint pro kreslení masky
    private Paint pAntiAlias;       // paint s antialiasingem pro kreslení textur
    private Canvas canvasOffline;   // canvas pro kreslení do offline části
    private Canvas canvasOnline;    // canvas pro kreslení do online části

    // výpočet cest
    private SpeedometerMaskPaths maskPaths;     // výpočet cest pro masky

    // konstruktory
    public SpeedMeterView(Context context) {
        super(context);
        init();
    }

    public SpeedMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpeedMeterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /*
    Jednorázová inicializace view po vytvoření
     */
    public void init() {

        // umazávač
        pTrans = new Paint();
        pTrans.setAntiAlias(true);
        pTrans.setColor(Color.TRANSPARENT);
        pTrans.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        pTrans.setAlpha(16);

        // kreslení textur
        pAntiAlias = new Paint();
        pAntiAlias.setFilterBitmap(true);

        // výpočet masek
        maskPaths = new SpeedometerMaskPaths(0, 240, 132, 275, 64, 5);

        // animátor
        animator = new ValueAnimator();
        animator.setDuration(500);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(this);

        // poč. hodnota
        value = 0;
    }

    /**
     * Připraví bitmapy  rozsvíceného i zhasnutého tachometru
     * - při volání již musí být známá přesná velikos plochy zabírané tímto view
     */
    private void initBitmaps() {

        // inicializace bitmap
        bmpOffline = BitmapFactory.decodeResource(getResources(), R.drawable.gauge_off);
        bmpOnline = BitmapFactory.decodeResource(getResources(), R.drawable.gauge_on);
        bmpOfflineMasked = Bitmap.createBitmap(tachoSize, tachoSize, Config.ARGB_8888);
        bmpOnlineMasked = Bitmap.createBitmap(tachoSize, tachoSize, Config.ARGB_8888);

        // canvas pro kreslení do překryvu - uložení výchozí stavu nezměněných textur
        canvasOnline = new Canvas(bmpOnlineMasked);
        canvasOffline = new Canvas(bmpOfflineMasked);
        canvasOnline.drawBitmap(bmpOnline, null, new Rect(0, 0, tachoSize, tachoSize), pAntiAlias);
        canvasOffline.drawBitmap(bmpOffline, null, new Rect(0, 0, tachoSize, tachoSize), pAntiAlias);
        canvasOnline.save();
        canvasOffline.save();
    }

    /**
     * Nastaví hodnotu na tachometru
     * - hodnota se bude animovat (bežící animace bude okamžitě nahrazena novou)
     *
     * @param newValue Nová hodnota
     */
    public void setValue(int newValue) {

        if (oldValue == newValue){
            return;
        }

        // overflow min a max hodnot
        if (newValue > maskPaths.maxValue) {
            newValue = (int) maskPaths.maxValue;
        }
        if (newValue < maskPaths.minValue) {
            newValue = (int) maskPaths.minValue;
        }

        // cílová hodnota
        targetValue = newValue;

        // stará a nová hodnota
        oldValue = value;
        value = newValue;

        // animace
        animator.cancel();
        animator.setIntValues(oldValue, value);
        animator.start();
    }

    public void setSecondValue(int newValue) {
        secValueEnabled = true;
        secValue = newValue;
    }

    /**
     * Nastaví minimální a maximální hodnotu na tachometru
     * - vizuální parametr kt. ovlivní dosah rozsvícené části
     *
     * @param min Min hodnota odpovídající 100% zahsnutému stavu
     * @param max Max hodnota odpovídající 100% rozsvícenému stavu
     */
    public void setMinMax(int min, int max) {
        maskPaths.minValue = min;
        maskPaths.maxValue = max;
    }

    /**
     * Povolí nebo zakáže zobrazení v tisících
     * - True - 7.9, False - 7923 apod.
     *
     * @param enabled Povoleno nebo zakázáno
     */
    public void setDisplayInThousands(boolean enabled) {
        displayInThousands = enabled;
    }

    /**
     * Handler pro update stavu animace
     * - zneplatní view, čímž vyvolá překreslení tachometru pro novou animovanou hodnotu
     *
     * @param valueAnimator Nová hodnota, již dosáhl animátor
     */
    @Override
    public void onAnimationUpdate(ValueAnimator valueAnimator) {
        value = (Integer) valueAnimator.getAnimatedValue();
        this.postInvalidate();
    }


    /*
        Třída pro počítání vektorových masek
     */
    private class SpeedometerMaskPaths {

        // pevně nastavené hodnoty
        public float minValue;    // min možná hodnota na tacho
        public float maxValue;    // max možná hodnota
        public final int startAngle;    // úhel kde začíná tacho
        public final int totalAngle;    // max plný úhel od počátečního
        public final int numPieces;     // počet kousků na které se tacho dělí
        public final int alphaSteps;    // polik kroků v nastavení prohlednosti má poslední kousek

        // hodnoty pro hlavní kousky - jednorázové výpočty
        public final float anglePerPiece;          // jaký úhel zabírá jeden kousek (jedna čárka) na tachometru
        public final int availPieces;            // kousky k dispozici - tj. v rozmezí vstupnch úhlů
        public final float percentPerPiece;        // procenta potřebná k obsazení jednoho kousku
        public final int invertedAngleOffset;    // úvodní hodnota úhlu masky - velikost nevybarveného prostoru na tacho
        public final int invertedStartAngle;     // začátek invertované masky - musí vymaskovat nevybarvený prostor a navíc vyplněný úhel tacho

        // hodnoty hlavních kousků - proměnné
        public float percentage;        // kolik procent z maxima je dosaženo
        public int pieces;              // kolik hlavních kousků se zobrazí
        int takenAngle;                 // plně obsazený úhel
        int invertedTakenAngle;         // invertovaný zabraný úhel

        // hodnoty vedlejších kousků
        float percentPerSubPiece;       // proc na jeden subpiece
        float leftOverPerc;             // procenta zbylá pro rozdělení do subpieců
        int subPieces;                  // počet subpieců
        float alphaPerSubPiece;         // o kolik bodů stoupne alpha posledního kousku za jeden subpiece
        int alpha;                      // finální průhlednost posledního kousku
        int invertedAlpha;              // invertovaná průhlednost - pro pozadí (255 - alpha)
        int startAngleSubPiece;         // začátek posledního kousku

        // střed a pozice
        float value;                    // aktuální hodnota na tacho
        int centerX;                    // souř. centra
        int centerY;                    //
        int radius;                     // poloměr
        int boxLeft;                    // souřadnice box modelu
        int boxRight;                   // - tj box který by měl ohraničovat komponentu
        int boxTop;                     // - souřadnice jsou absolutní
        int boxBottom;


        // cesty
        Path positiveMaskPrimaryPath;   // positivní - odkrývá dosaženou část - cesta pro plné odkrytí
        Path positiveMaskSecondaryPath; // positivní - odkrývá dosaženou část - cesta k částečnému odkrytí
        Path negativeMaskPrimaryPath;   // negativní - odkrývá prázdnou část - cesta pro plné odkrytí
        Path negativeMaskSecondaryPath; // negativní - odkrývá prázdnou část - cesta k částečnému odkrytí

        public SpeedometerMaskPaths(float _minValue, float _maxValue, int _startAngle, int _totalAngle, int _numPieces, int _alphaSteps) {
            minValue = _minValue;
            maxValue = _maxValue;
            startAngle = _startAngle;
            totalAngle = _totalAngle;
            numPieces = _numPieces;
            alphaSteps = _alphaSteps;

            // jednorázové výpočty
            anglePerPiece = 360f / numPieces;
            availPieces = (int) (Math.ceil((float) totalAngle / anglePerPiece));
            percentPerPiece = 1f / availPieces;
            invertedAngleOffset = 360 - totalAngle;
            invertedStartAngle = startAngle - invertedAngleOffset;
        }

        public void recalcValues() {
            // %
            percentage = Math.max(0, (value - minValue) / (maxValue - minValue));

            // kolik kousků je zabraných, jaký to dělá úhel
            pieces = (int) (percentage / percentPerPiece);
            takenAngle = (int) (pieces * anglePerPiece);

            // alpha posledního kousku
            percentPerSubPiece = percentPerPiece / (float) alphaSteps;
            leftOverPerc = percentage - (float) pieces * percentPerPiece;
            subPieces = (int) ((float) leftOverPerc / (float) percentPerSubPiece);
            alphaPerSubPiece = (float) 255 / (float) alphaSteps;
            alpha = (int) (Math.min(255, Math.max(0, subPieces * alphaPerSubPiece)));
            invertedAlpha = 255 - alpha;
            startAngleSubPiece = startAngle + takenAngle;                           // startuje tam, kde končí minulý koláč, jinak by bylo prázdné místo

            // inverze úhlů
            invertedTakenAngle = (int) (totalAngle - takenAngle + invertedAngleOffset - anglePerPiece);
        }

        /*
        Přepočítá cesty masky v souřadnicích relativních k maskované bitmapě
         */
        public void recalcPaths() {

            // nové cesty
            positiveMaskPrimaryPath = new Path();
            positiveMaskSecondaryPath = new Path();
            negativeMaskPrimaryPath = new Path();
            negativeMaskSecondaryPath = new Path();

            // střed kruhu - přesun kreslítka
            int rHalf = radius / 2;
            positiveMaskPrimaryPath.setLastPoint(rHalf, rHalf);
            positiveMaskSecondaryPath.setLastPoint(rHalf, rHalf);
            negativeMaskPrimaryPath.setLastPoint(rHalf, rHalf);
            negativeMaskSecondaryPath.setLastPoint(rHalf, rHalf);
            positiveMaskPrimaryPath.moveTo(rHalf, rHalf);
            positiveMaskSecondaryPath.moveTo(rHalf, rHalf);
            negativeMaskPrimaryPath.moveTo(rHalf, rHalf);
            negativeMaskSecondaryPath.moveTo(rHalf, rHalf);

            // spojnice středu a začátku kruhu
            positiveMaskPrimaryPath.lineTo(rHalf + (float) Math.cos(Math.toRadians(startAngle)) * (rHalf),
                    rHalf + (float) Math.sin(Math.toRadians(startAngle)) * (rHalf));
            positiveMaskSecondaryPath.lineTo(rHalf + (float) Math.cos(Math.toRadians(startAngleSubPiece)) * (rHalf),
                    rHalf + (float) Math.sin(Math.toRadians(startAngleSubPiece)) * (rHalf));
            negativeMaskPrimaryPath.lineTo(rHalf + (float) Math.cos(Math.toRadians(startAngle)) * (rHalf),
                    rHalf + (float) Math.sin(Math.toRadians(startAngle)) * (rHalf));
            negativeMaskSecondaryPath.lineTo(rHalf + (float) Math.cos(Math.toRadians(startAngleSubPiece)) * (rHalf),
                    rHalf + (float) Math.sin(Math.toRadians(startAngleSubPiece)) * (rHalf));

            // kruhová výseč
            positiveMaskPrimaryPath.addArc(new RectF(0, 0, radius, radius), startAngle, -invertedTakenAngle);
            positiveMaskSecondaryPath.addArc(new RectF(0, 0, radius, radius), startAngleSubPiece, anglePerPiece);
            negativeMaskPrimaryPath.addArc(new RectF(0, 0, radius, radius), invertedStartAngle, takenAngle + invertedAngleOffset);
            negativeMaskSecondaryPath.addArc(new RectF(0, 0, radius, radius), startAngleSubPiece, anglePerPiece);

            // spojnice zpět do středu
            positiveMaskPrimaryPath.lineTo(rHalf, rHalf);
            positiveMaskSecondaryPath.lineTo(rHalf, rHalf);
            negativeMaskPrimaryPath.lineTo(rHalf, rHalf);
            negativeMaskSecondaryPath.lineTo(rHalf, rHalf);

        }

        public void recalcAll() {
            recalcValues();
            recalcPaths();
        }

        public void updateSize(int _centerX, int _centerY, int _radius) {
            centerX = _centerX;
            centerY = _centerY;
            radius = _radius;
            boxLeft = centerX - radius / 2;   // left < right
            boxRight = centerX + radius / 2;
            boxTop = centerY - radius / 2;    // top < bottom
            boxBottom = centerY + radius / 2;
        }

        public void updateValue(float _value) {
            value = _value;
        }

    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(DB.getRedrawSpeedMeter()) {

            // vypočítá a vytvoří masku
            maskPaths.updateSize(width / 2, height / 2, tachoSize);
            maskPaths.updateValue(value);
            maskPaths.recalcAll();

            // nakreslí obě plné textury
            canvasOnline.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            canvasOffline.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            canvasOnline.drawBitmap(bmpOnline, null, new Rect(0, 0, tachoSize, tachoSize), pAntiAlias);
            canvasOffline.drawBitmap(bmpOffline, null, new Rect(0, 0, tachoSize, tachoSize), pAntiAlias);

            // odmaže maskovanou část
            pTrans.setAlpha(255);
            canvasOnline.drawPath(maskPaths.positiveMaskPrimaryPath, pTrans);       // online plná
            pTrans.setAlpha(maskPaths.invertedAlpha);
            canvasOnline.drawPath(maskPaths.positiveMaskSecondaryPath, pTrans);     // online průhledná
            pTrans.setAlpha(255);
            canvasOffline.drawPath(maskPaths.negativeMaskPrimaryPath, pTrans);      // offline plná
            pTrans.setAlpha(maskPaths.alpha);
            canvasOffline.drawPath(maskPaths.negativeMaskSecondaryPath, pTrans);   // offline průhledná

            // zakreslí maskované bitmapy
            canvas.drawBitmap(bmpOfflineMasked, maskPaths.boxLeft, maskPaths.boxTop, null);     // pozadí
            canvas.drawBitmap(bmpOnlineMasked, maskPaths.boxLeft, maskPaths.boxTop, null);     // popředí

        }

        // získáme hodnotu
        String valueText;
        if (displayInThousands) {
            double val = Math.round((double) targetValue / 100);
            valueText = "" + (val / 10);
        } else {
            valueText = "" + targetValue;
        }

        // nakreslíme texty
        Paint textPaint = new Paint();
        textPaint.setTextSize(180);
        textPaint.setAntiAlias(true);
        textPaint.setColor(Color.WHITE);
        float txtH = textPaint.ascent() - textPaint.descent();
        float txtW = textPaint.measureText(valueText);
        canvas.drawText(valueText, tachoSize / 2 - txtW / 2, tachoSize / 2 - txtH / 2, textPaint);

        // druhý text
        if (secValueEnabled) {
            String txt = "[ " + secValue + " ]";
            Paint secPaint = new Paint();
            secPaint.setTextSize(90);
            secPaint.setAntiAlias(true);
            secPaint.setColor(Color.WHITE);
            float stxtH = secPaint.ascent() - secPaint.descent();
            float stxtW = secPaint.measureText(txt);
            canvas.drawText(txt, tachoSize / 2 - stxtW / 2, tachoSize / 2 - stxtH / 2 + 180, secPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        width = MeasureSpec.getSize(widthMeasureSpec);
        height = MeasureSpec.getSize(heightMeasureSpec);

        // tachoSize, menší z rozměrů
        tachoSize = (int) Math.min(width, height);

        // inicializujeme bitmapy (již známe rozměry)
        initBitmaps();

        super.onMeasure(widthMeasureSpec, heightMeasureSpec - getTop());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }
}



