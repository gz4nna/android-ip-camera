using System.Net.NetworkInformation;

namespace Sample;

public partial class Form1 : Form
{
    #region 
    private string ip;
    private string port;

    private List<string> availableDevices = new();

    private static readonly HttpClient httpClient = new() { Timeout = TimeSpan.FromSeconds(1) };

    private MjpegStreamReader streamReader;    
    
    private Bitmap? currentFrame; // µ±Ç°Ö¡»º´æ
    private readonly object frameLock = new(); // Ïß³ÌËø£¬±ÜÃâ UI Ïß³Ì³åÍ»
    #endregion

    public Form1()
    {
        InitializeComponent();
        port = "4747";
        portTextBox.Text = port;
        FindUsbPhoneDroidCams();

        if(availableDevices.Count == 0) return;

        ipListComboBox.Items.AddRange(availableDevices.ToArray());
        ipListComboBox.SelectedIndex = 0;            
        videoPanel.DoubleBuffered(true); // ¿ªÆôË«»º³å
    }
    
    
    private void FindUsbPhoneDroidCams()
    {
        bool IsPrivateIP(string ip) => ip.StartsWith("192.168.") || ip.StartsWith("10.") || ip.StartsWith("172.16.");

        foreach (NetworkInterface networkInterface in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (networkInterface.OperationalStatus != OperationalStatus.Up) continue;

            foreach (GatewayIPAddressInformation gatewayIPAddressInformation in networkInterface.GetIPProperties().GatewayAddresses)
            {
                string gatewayIP = gatewayIPAddressInformation.Address.ToString();

                if (!IsPrivateIP(gatewayIP)) continue;

                string mjpegUrl = $"http://{gatewayIP}:{port}/video";

                if (CheckURL(mjpegUrl)) availableDevices.Add(gatewayIP);
            }
        }
    }

    private bool CheckURL(string url)
    {
        try
        {
            using var response = httpClient.GetAsync(url, HttpCompletionOption.ResponseHeadersRead).Result;
            return response.IsSuccessStatusCode;
        }
        catch
        {
            return false;
        }
    }

    #region event collection
    private void connectDeviceButton_Click(object sender, EventArgs e)
    {
        if (string.IsNullOrEmpty(ip)) return;
        
        string mjpegUrl = $"http://{ip}:{port}/video";
                   
        streamReader = new(mjpegUrl);
        streamReader.FrameReady += img =>
        {
            lock (frameLock)
            {
                currentFrame?.Dispose();
                currentFrame = (Bitmap)img.Clone();
            }
            videoPanel.Invalidate(); // ´¥·¢ÖØ»æ
        };
        streamReader.Start();
    }

    private void IpListComboBox_SelectedIndexChanged(object sender, EventArgs e) => ip = availableDevices[ipListComboBox.SelectedIndex];

    private void Form1_FormClosed(object sender, FormClosedEventArgs e) => streamReader?.Stop();

    private void PanelVideo_Paint(object? sender, PaintEventArgs e)
    {
        lock (frameLock)
        {
            if (currentFrame != null)
            {
                e.Graphics.DrawImage(currentFrame, 0, 0, videoPanel.Width, videoPanel.Height);
            }
        }
    }
    #endregion
}

public static class ControlExtensions
{
    public static void DoubleBuffered(this Control control, bool enable)
    {
        var prop = typeof(Control).GetProperty("DoubleBuffered",
            System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance);
        prop?.SetValue(control, enable, null);
    }
}
