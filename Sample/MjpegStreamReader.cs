using System;
using System.Collections.Concurrent;
using System.IO;
using System.Net.Http;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Drawing;
using System.Windows.Forms;

namespace Sample
{
    internal class MjpegStreamReader
    {
        private readonly string _url;
        private CancellationTokenSource _cancellation;
        private readonly ConcurrentQueue<byte[]> _frameQueue = new();
        private Task _decodeTask;
        private string _boundary = null;
        private readonly byte[] _jpegHeader = { 0xFF, 0xD8 };
        private readonly byte[] _jpegFooter = { 0xFF, 0xD9 };
        private readonly SynchronizationContext _uiContext;

        public event Action<Image> FrameReady;

        public MjpegStreamReader(string url)
        {
            _url = url;
            _uiContext = SynchronizationContext.Current;
        }

        public void Start()
        {
            _cancellation = new CancellationTokenSource();
            _decodeTask = Task.Run(() => DecodeLoop(_cancellation.Token));
            Task.Run(() => ReadLoop(_cancellation.Token));
        }

        public void Stop() => _cancellation?.Cancel();

        private async Task ReadLoop(CancellationToken token)
        {
            try
            {
                using var client = new HttpClient();
                using var stream = await client.GetStreamAsync(_url);

                byte[] buffer = new byte[8192];
                MemoryStream imageBuffer = new();
                bool insideJpeg = false;

                while (!token.IsCancellationRequested)
                {
                    int bytesRead = await stream.ReadAsync(buffer, 0, buffer.Length, token);
                    if (bytesRead == 0) continue;

                    for (int i = 0; i < bytesRead; i++)
                    {
                        if (i == 0) continue; // 避免 buffer[-1]

                        if (insideJpeg)
                        {
                            imageBuffer.WriteByte(buffer[i]);

                            if (buffer[i - 1] == _jpegFooter[0] && buffer[i] == _jpegFooter[1])
                            {
                                _frameQueue.Enqueue(imageBuffer.ToArray());
                                imageBuffer.SetLength(0);
                                insideJpeg = false;
                            }
                        }
                        else if (buffer[i - 1] == _jpegHeader[0] && buffer[i] == _jpegHeader[1])
                        {
                            imageBuffer.SetLength(0);
                            imageBuffer.WriteByte(buffer[i - 1]);
                            imageBuffer.WriteByte(buffer[i]);
                            insideJpeg = true;
                        }
                    }

                    if (_boundary == null)
                    {
                        string header = Encoding.ASCII.GetString(buffer, 0, Math.Min(bytesRead, 1024));
                        if (header.Contains("Content-Type: multipart/x-mixed-replace"))
                        {
                            int index = header.IndexOf("boundary=", StringComparison.OrdinalIgnoreCase);
                            if (index > 0)
                            {
                                _boundary = header[(index + 9)..].Split('\r', '\n')[0];
                            }
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine("MJPEG Read Error: " + ex.Message);
            }
        }

        private void DecodeLoop(CancellationToken token)
        {
            while (!token.IsCancellationRequested)
            {
                if (_frameQueue.TryDequeue(out byte[] jpegData))
                {
                    // 丢弃过时帧
                    while (_frameQueue.Count > 1)
                        _frameQueue.TryDequeue(out _);

                    if (jpegData == null || jpegData.Length < 1024 || !ContainsSequence(_jpegFooter, jpegData))
                    {
                        Console.WriteLine("Skipping invalid JPEG frame.");
                        continue;
                    }

                    try
                    {
                        using var ms = new MemoryStream(jpegData);
                        var img = Image.FromStream(ms);
                        _uiContext.Post(_ => FrameReady?.Invoke(img), null);
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"MJPEG Decode Error: {ex.Message}, JPEG Length: {jpegData.Length}");
                    }
                }
                else
                {
                    Thread.Sleep(5);
                }
            }
        }

        private bool ContainsSequence(byte[] sequence, byte[] array)
        {
            for (int i = 0; i <= array.Length - sequence.Length; i++)
            {
                bool match = true;
                for (int j = 0; j < sequence.Length; j++)
                {
                    if (array[i + j] != sequence[j])
                    {
                        match = false;
                        break;
                    }
                }
                if (match) return true;
            }
            return false;
        }
    }
}
