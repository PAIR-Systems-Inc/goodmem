using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace Pairsystems.Goodmem.Client.Tests
{
    [TestClass]
    public class ClientTests
    {
        [TestMethod]
        public void TestClientCreation()
        {
            var client = new Client("http://localhost:8080");
            Assert.IsNotNull(client);
        }
    }
}
