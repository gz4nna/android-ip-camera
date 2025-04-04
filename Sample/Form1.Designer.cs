namespace Sample
{
    partial class Form1
    {
        /// <summary>
        ///  Required designer variable.
        /// </summary>
        private System.ComponentModel.IContainer components = null;

        /// <summary>
        ///  Clean up any resources being used.
        /// </summary>
        /// <param name="disposing">true if managed resources should be disposed; otherwise, false.</param>
        protected override void Dispose(bool disposing)
        {
            if (disposing && (components != null))
            {
                components.Dispose();
            }
            base.Dispose(disposing);
        }

        #region Windows Form Designer generated code

        /// <summary>
        ///  Required method for Designer support - do not modify
        ///  the contents of this method with the code editor.
        /// </summary>
        private void InitializeComponent()
        {
            pictureBox1 = new PictureBox();
            connectDeviceButton = new Button();
            ipListComboBox = new ComboBox();
            globalHintTextLabel = new Label();
            ipListLabel = new Label();
            portTextBox = new TextBox();
            portTextLabel = new Label();
            ((System.ComponentModel.ISupportInitialize)pictureBox1).BeginInit();
            SuspendLayout();
            // 
            // pictureBox1
            // 
            pictureBox1.Location = new Point(1000, 200);
            pictureBox1.Name = "pictureBox1";
            pictureBox1.Size = new Size(800, 600);
            pictureBox1.TabIndex = 0;
            pictureBox1.TabStop = false;
            // 
            // connectDeviceButton
            // 
            connectDeviceButton.Location = new Point(52, 450);
            connectDeviceButton.Name = "connectDeviceButton";
            connectDeviceButton.Size = new Size(303, 46);
            connectDeviceButton.TabIndex = 1;
            connectDeviceButton.Text = "start connect";
            connectDeviceButton.UseVisualStyleBackColor = true;
            connectDeviceButton.Click += connectDeviceButton_Click;
            // 
            // ipListComboBox
            // 
            ipListComboBox.FormattingEnabled = true;
            ipListComboBox.Location = new Point(394, 200);
            ipListComboBox.Name = "ipListComboBox";
            ipListComboBox.Size = new Size(242, 39);
            ipListComboBox.TabIndex = 2;
            ipListComboBox.SelectedIndexChanged += IpListComboBox_SelectedIndexChanged;
            // 
            // globalHintTextLabel
            // 
            globalHintTextLabel.AutoSize = true;
            globalHintTextLabel.Location = new Point(52, 84);
            globalHintTextLabel.Name = "globalHintTextLabel";
            globalHintTextLabel.Size = new Size(163, 31);
            globalHintTextLabel.TabIndex = 3;
            globalHintTextLabel.Text = "hint hint hint";
            // 
            // ipListLabel
            // 
            ipListLabel.AutoSize = true;
            ipListLabel.Location = new Point(52, 200);
            ipListLabel.Name = "ipListLabel";
            ipListLabel.Size = new Size(281, 31);
            ipListLabel.TabIndex = 4;
            ipListLabel.Text = "choose your device's ip";
            // 
            // portTextBox
            // 
            portTextBox.Location = new Point(401, 310);
            portTextBox.Name = "portTextBox";
            portTextBox.Size = new Size(200, 38);
            portTextBox.TabIndex = 5;
            // 
            // portTextLabel
            // 
            portTextLabel.AutoSize = true;
            portTextLabel.Location = new Point(52, 317);
            portTextLabel.Name = "portTextLabel";
            portTextLabel.Size = new Size(270, 31);
            portTextLabel.TabIndex = 6;
            portTextLabel.Text = "edit your device's port";
            // 
            // Form1
            // 
            AutoScaleDimensions = new SizeF(14F, 31F);
            AutoScaleMode = AutoScaleMode.Font;
            ClientSize = new Size(1894, 1009);
            Controls.Add(portTextLabel);
            Controls.Add(portTextBox);
            Controls.Add(ipListLabel);
            Controls.Add(globalHintTextLabel);
            Controls.Add(ipListComboBox);
            Controls.Add(connectDeviceButton);
            Controls.Add(pictureBox1);
            Name = "Form1";
            Text = "Form1";
            FormClosed += Form1_FormClosed;
            ((System.ComponentModel.ISupportInitialize)pictureBox1).EndInit();
            ResumeLayout(false);
            PerformLayout();
        }
        #endregion

        private PictureBox pictureBox1;
        private Button connectDeviceButton;
        private ComboBox ipListComboBox;
        private Label globalHintTextLabel;
        private Label ipListLabel;
        private TextBox portTextBox;
        private Label portTextLabel;
    }
}
