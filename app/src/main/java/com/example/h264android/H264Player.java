package com.example.h264android;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class H264Player implements Runnable{

    private static final String TAG = "H264Player";

    private File file;
    private Surface surface;

    // DSP的代言人，硬编解码器
    MediaCodec mediaCodec;

    public H264Player(File file, Surface surface, int width, int height) {
        this.file = file;
        this.surface = surface;
        try {
            mediaCodec = MediaCodec.createDecoderByType("video/avc");

            // 自己的参数复制进去 交给DSP进行创建
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            // 将surface配置给DSP，mediaCodec会将解码好的一帧图像渲染到surface上
            mediaCodec.configure(mediaFormat, surface, null, 0);
        } catch (IOException e) {
            Log.e(TAG, "H264Player: ", e);
        }
        
    }

    public void play() {
        Log.d(TAG, "play: ");
        mediaCodec.start();
        new Thread(this).start();
    }

    @Override
    public void run() {
        // MediaCodec 提供了一个长度为8的队列容器
        // 通过这个队列 CPU 把H264数据送给 DSP，DSP 解码好后再将 YUE 数据送回给CPU
        Log.d(TAG, "run: decode");
        decodeH264();
    }

    /**
     * 将H264文件解码为图像文件
     */
    private void decodeH264() {
        byte[] bytes = null;
        try {
            bytes = getBytes(file);
        } catch (IOException e) {
            Log.e(TAG, "decodeH264: ", e);
        }

        int startIndex = 0;

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            // 拿到下一帧分隔符
            int nextFrameStart = findByFrame(bytes, startIndex + 2, bytes.length);
            Log.d(TAG, "decodeH264: nextFrameStart is " + nextFrameStart);
            // 拿到队列容器
            int index = mediaCodec.dequeueInputBuffer(10000);
            if (index >= 0) {
                //  拿到一个容器
                ByteBuffer byteBuffer = mediaCodec.getInputBuffer(index);
                // 数据要丢多少进去呢？ 按帧来丢
                // 如何知道1帧的内容？ 通过分隔符来划分一帧
                byteBuffer.put(bytes, startIndex, nextFrameStart - startIndex);
                Log.d(TAG, "decodeH264: frame size is " + (nextFrameStart - startIndex));
                // 告诉DSP数据放进了第几个buffer，将数据交给DSP处理
                mediaCodec.queueInputBuffer(index, 0, nextFrameStart - startIndex, 0, 0);
            }
            // 会dequeue一个Buffer，并把Buffer信息填入到info中
            int outIndex = mediaCodec.dequeueOutputBuffer(info, 10000);
            if (outIndex >= 0) {
                mediaCodec.releaseOutputBuffer(outIndex, true);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            startIndex = nextFrameStart;
        }

    }

    /**
     * 返回下一个分隔符的位置
     * @param bytes H264字节流
     * @param start 上一个分隔符
     * @param totalSize 字节流总大小（字节）
     * @return 下一个分隔符的位置，没有找到则返回-1
     */
    private int findByFrame(byte[] bytes, int start, int totalSize) {
        for (int i = start; i <= totalSize - 4; i++) {
            if (((bytes[i] == 0x00) && (bytes[i + 1] == 0x00) && (bytes[i + 2] == 0x00) && (bytes[i + 3] == 0x01))
            || ((bytes[i] == 0x00) && (bytes[i + 1] == 0x00) && (bytes[i + 2] == 0x01))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 将H264文件读取到内存中
     * @param file 文件
     * @return 字节数组
     * @throws IOException 文件不存在等IO异常
     */
    private byte[] getBytes(File file) throws IOException{
        InputStream is = new DataInputStream(new FileInputStream(file));
        int len;
        int size = 1024;
        byte[] buf;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        buf = new byte[size];
        while((len = is.read(buf, 0, size)) != -1) {
            bos.write(buf, 0, len);
        }
        buf = bos.toByteArray();
        return buf;
    }

    // 数据源
    // 解码器
    // 显示 目的地 surface
    // MediaCodec
}
