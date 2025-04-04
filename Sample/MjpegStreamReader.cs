namespace Sample;

internal class MjpegStreamReader
{
    private readonly string _url;
    private CancellationTokenSource _cancellation;
    public event Action<Image> FrameReady;

    public MjpegStreamReader(string url)
    {
        _url = url;
    }

    public void Start()
    {
        _cancellation = new CancellationTokenSource();
        Task.Run(() => ReadLoop(_cancellation.Token));
    }

    public void Stop()
    {
        _cancellation?.Cancel();
    }

    private async Task ReadLoop(CancellationToken token)
    {
        try
        {
            using var client = new HttpClient();
            using var stream = await client.GetStreamAsync(_url);

            var buffer = new byte[4096];
            var imageBuffer = new MemoryStream();

            while (!token.IsCancellationRequested)
            {
                int bytesRead = await stream.ReadAsync(buffer, 0, buffer.Length, token);
                if (bytesRead == 0) continue;

                imageBuffer.Write(buffer, 0, bytesRead);

                if (TryExtractJpeg(imageBuffer, out byte[] jpeg))
                {
                    using var ms = new MemoryStream(jpeg);
                    var img = Image.FromStream(ms);
                    FrameReady?.Invoke(img);
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine("MJPEG Error: " + ex.Message);
        }
    }

    private bool TryExtractJpeg(MemoryStream buffer, out byte[] jpeg)
    {
        jpeg = null;
        byte[] data = buffer.ToArray();

        int start = FindMarker(data, new byte[] { 0xFF, 0xD8 }); // SOI
        int end = FindMarker(data, new byte[] { 0xFF, 0xD9 }, start); // EOI

        if (start != -1 && end != -1 && end > start)
        {
            int length = end - start + 2;
            jpeg = new byte[length];
            Array.Copy(data, start, jpeg, 0, length);

            // 移除已消费数据
            byte[] leftover = new byte[data.Length - (end + 2)];
            Array.Copy(data, end + 2, leftover, 0, leftover.Length);
            buffer.SetLength(0);
            buffer.Write(leftover, 0, leftover.Length);
            return true;
        }

        return false;
    }

    private int FindMarker(byte[] data, byte[] marker, int start = 0)
    {
        for (int i = start; i < data.Length - marker.Length; i++)
        {
            bool match = true;
            for (int j = 0; j < marker.Length; j++)
            {
                if (data[i + j] != marker[j])
                {
                    match = false;
                    break;
                }
            }

            if (match) return i;
        }

        return -1;
    }
}
