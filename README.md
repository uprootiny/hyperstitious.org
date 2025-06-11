# Hyperstitious.org

A static site built with [Zola](https://www.getzola.org/) using the [Linkita](https://www.getzola.org/themes/linkita/) theme.

## Development

### Prerequisites

- [Zola](https://www.getzola.org/documentation/getting-started/installation/) static site generator

### Local Development

1. Clone the repository with submodules:
   ```bash
   git clone --recurse-submodules https://github.com/your-username/hyperstitious.org.git
   cd hyperstitious.org
   ```

2. Serve the site locally:
   ```bash
   zola serve
   ```

3. Visit `http://127.0.0.1:1111` to view the site

### Project Structure

```
├── config.toml           # Site configuration
├── content/              # Content directory
│   └── _index.md        # Home page content
├── static/              # Static assets
├── templates/           # Custom templates (if any)
├── themes/              # Theme directory
│   └── linkita/         # Linkita theme (git submodule)
└── .github/
    └── workflows/
        └── gh-pages.yml # GitHub Actions deployment
```

## Content Management

### Front Matter

The site uses TOML front matter. Example for the home page (`content/_index.md`):

```toml
+++
title = "Welcome"
description = "A simple landing page"
sort_by = "date"
paginate_by = 5

[extra]
profile = "your-username"
+++
```

### Available Front Matter Options

- `title`: Page title
- `description`: Page description  
- `date`: Publication date
- `updated`: Last updated date
- `categories`: List of categories
- `tags`: List of tags

#### Extra Options
- `comment`: Enable/disable comments
- `math`: Enable KaTeX for math rendering
- `mermaid`: Enable Mermaid diagrams
- `cover`: Add cover image with alt text

## Deployment

The site automatically deploys to GitHub Pages via GitHub Actions when changes are pushed to the `main` branch.

### GitHub Pages Setup

1. **Repository Settings**: Go to Settings → Pages
2. **Source**: Select "Deploy from a branch"
3. **Branch**: Select `gh-pages`
4. **Folder**: Select `/ (root)`

### Deployment Workflow

The deployment is handled by `.github/workflows/gh-pages.yml`:

- Triggers on push to `main` branch
- Uses the `shalzz/zola-deploy-action` 
- Builds the site and deploys to `gh-pages` branch
- Uses the built-in `GITHUB_TOKEN` (no manual token setup required)

## Theme

This site uses the [Linkita](https://www.getzola.org/themes/linkita/) theme, added as a git submodule.

### Theme Updates

To update the theme:

```bash
git submodule update --remote themes/linkita
git add themes/linkita
git commit -m "Update linkita theme"
```

## Configuration

Key settings in `config.toml`:

- `base_url`: Set to your domain (https://hyperstitious.org)
- `theme`: Set to "linkita"
- `build_search_index`: Enables search functionality
- `highlight_code`: Enables syntax highlighting

## Local Commands

- `zola serve`: Start development server
- `zola build`: Build site for production
- `zola check`: Check for errors