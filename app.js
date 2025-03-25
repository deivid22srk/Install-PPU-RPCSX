import { createApp } from 'vue';
import config from './config.js';

const app = createApp({
    data() {
        return {
            currentView: 'home',
            gamesList: [],
            filteredGames: [],
            selectedGame: null,
            searchQuery: '',
            darkMode: localStorage.getItem('darkMode') === 'true',
            notification: {
                show: false,
                message: '',
                type: 'success'
            },
            isLoading: true
        };
    },
    methods: {
        showHome() {
            this.currentView = 'home';
        },
        showGames() {
            this.currentView = 'games';
            this.filterGames();
        },
        showAbout() {
            this.currentView = 'about';
        },
        showGameDetail(game) {
            this.selectedGame = game;
            this.currentView = 'gameDetail';
        },
        filterGames() {
            if (!this.searchQuery) {
                this.filteredGames = this.gamesList;
            } else {
                const query = this.searchQuery.toLowerCase();
                this.filteredGames = this.gamesList.filter(game => 
                    game.title.toLowerCase().includes(query) || 
                    game.id.toLowerCase().includes(query)
                );
            }
        },
        async fetchGamesList() {
            try {
                this.isLoading = true;
                const response = await fetch(config.gamesListUrl);
                if (!response.ok) {
                    throw new Error('Failed to fetch games list');
                }
                const data = await response.json();
                this.gamesList = data;
                this.filteredGames = data;
                this.isLoading = false;
            } catch (error) {
                console.error('Error fetching games:', error);
                this.showNotification('Failed to load games list. Please try again later.', 'error');
                this.isLoading = false;
            }
        },
        installPPU(uri) {
            this.showNotification('Installation started for: ' + uri.split('/').pop(), 'success');
            // Placeholder for actual installation functionality
        },
        toggleDarkMode() {
            this.darkMode = !this.darkMode;
            localStorage.setItem('darkMode', this.darkMode);
            if (this.darkMode) {
                document.body.classList.add('dark-mode');
            } else {
                document.body.classList.remove('dark-mode');
            }
        },
        showNotification(message, type = 'success') {
            this.notification.message = message;
            this.notification.type = type;
            this.notification.show = true;
            setTimeout(() => {
                this.closeNotification();
            }, 5000);
        },
        closeNotification() {
            this.notification.show = false;
        }
    },
    created() {
        this.fetchGamesList();
        if (this.darkMode) {
            document.body.classList.add('dark-mode');
        }
    }
});

app.mount('#app');