using System.Net.NetworkInformation;

namespace Sample
{
    public partial class Form1 : Form
    {
        private string ip;
        private string port;
        private MjpegStreamReader streamReader;
        private List<string> availableDevices = new();
        private static readonly HttpClient httpClient = new() { Timeout = TimeSpan.FromSeconds(1) };

        public Form1()
        {
            InitializeComponent();
            port = "4747";
            portTextBox.Text = port;
            FindUsbPhoneDroidCams();

            if(availableDevices.Count == 0) return;

            ipListComboBox.Items.AddRange(availableDevices.ToArray());
            ipListComboBox.SelectedIndex = 0;
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
                if (pictureBox1.Image != null)
                    pictureBox1.Image.Dispose(); // ÊÍ·Å¾ÉÍ¼Æ¬£¬·ÀÖ¹ÄÚ´æÐ¹Â©
                pictureBox1.Image = img;
            };
            streamReader.Start();
        }

        private void IpListComboBox_SelectedIndexChanged(object sender, EventArgs e) => ip = availableDevices[ipListComboBox.SelectedIndex];

        private void Form1_FormClosed(object sender, FormClosedEventArgs e) => streamReader?.Stop();
        #endregion
    }
}
