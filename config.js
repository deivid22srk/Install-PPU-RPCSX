// Configuration file for RPCS3 PPU Installer
const gamesList = []; // Will be populated from external source

export default {
    // List of games with PPU files
    gamesList: gamesList,
    
    // Site configuration
    siteConfig: {
        title: "RPCS3 PPU Installer",
        description: "Easy installation of PPU files for RPCS3 emulator",
        contact: "support@example.com"
    },
    
    // GitHub URL for games list
    gamesListUrl: "https://raw.githubusercontent.com/DEYVIDYT/Install-PPU-RPCS3/refs/heads/main/games.json"
};