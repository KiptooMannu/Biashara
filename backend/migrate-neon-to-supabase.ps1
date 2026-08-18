# BIASHARA: Data Migration Script - Neon to Supabase PostgreSQL
# This script backs up data from Neon and restores it to Supabase
#
# Prerequisites:
# - PostgreSQL client tools installed (pg_dump, psql)
# - Access to both Neon and Supabase database credentials
# - Sufficient disk space for database backup file
#
# Usage:
#   .\migrate-neon-to-supabase.ps1 -neonPassword "your_neon_password" -supabasePassword "your_supabase_password"

param(
    [Parameter(Mandatory=$true)]
    [string]$neonPassword,
    
    [Parameter(Mandatory=$true)]
    [string]$supabasePassword,
    
    [string]$backupDir = ".\database-backups",
    [string]$backupFile = "biashara-neon-backup.sql",
    [switch]$skipBackup = $false,
    [switch]$skipRestore = $false,
    [switch]$skipVerify = $false
)

# ================================================================
# CONFIGURATION
# ================================================================

$NEON_HOST = "ep-ancient-firefly-ayi4knqn-pooler.c-5.us-east-2.aws.neon.tech"
$NEON_PORT = "5432"
$NEON_USER = "neondb_owner"
$NEON_DB = "neondb"

$SUPABASE_HOST = "db.kdwjmcqiavdwnjigelsn.supabase.co"
$SUPABASE_PORT = "5432"
$SUPABASE_USER = "postgres"
$SUPABASE_DB = "postgres"

# ================================================================
# FUNCTIONS
# ================================================================

function Write-Header {
    param([string]$message)
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host $message -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$message)
    Write-Host "✓ $message" -ForegroundColor Green
}

function Write-Error-Custom {
    param([string]$message)
    Write-Host "✗ $message" -ForegroundColor Red
}

function Write-Info {
    param([string]$message)
    Write-Host "ℹ $message" -ForegroundColor Yellow
}

# Create backup directory
function Create-BackupDir {
    if (-not (Test-Path $backupDir)) {
        New-Item -ItemType Directory -Path $backupDir | Out-Null
        Write-Success "Created backup directory: $backupDir"
    }
}

# ================================================================
# PHASE 1: BACKUP FROM NEON
# ================================================================

function Backup-FromNeon {
    Write-Header "PHASE 1: Backing up data from Neon PostgreSQL"
    
    $fullBackupPath = Join-Path $backupDir $backupFile
    
    if ((Test-Path $fullBackupPath) -and -not $skipBackup) {
        Write-Info "Backup file already exists. Using existing backup."
        Write-Info "Path: $fullBackupPath"
        Write-Info "Use -skipBackup to skip this check"
        return $fullBackupPath
    }
    
    Write-Info "Connecting to Neon: $NEON_HOST"
    Write-Info "Database: $NEON_DB"
    Write-Info "User: $NEON_USER"
    
    try {
        # Set environment variable for password (pg_dump uses PGPASSWORD)
        $env:PGPASSWORD = $neonPassword
        
        Write-Info "Starting pg_dump... This may take several minutes depending on data size."
        
        & pg_dump `
            -h $NEON_HOST `
            -p $NEON_PORT `
            -U $NEON_USER `
            -d $NEON_DB `
            --no-password `
            --verbose `
            --format=plain `
            -f $fullBackupPath
        
        if ($LASTEXITCODE -eq 0) {
            $fileSize = (Get-Item $fullBackupPath).Length / 1MB
            Write-Success "Backup completed successfully"
            Write-Success "Backup file: $fullBackupPath"
            Write-Success "File size: $([math]::Round($fileSize, 2)) MB"
            return $fullBackupPath
        } else {
            Write-Error-Custom "pg_dump failed with exit code: $LASTEXITCODE"
            exit 1
        }
    }
    finally {
        # Clear the password from environment
        Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
    }
}

# ================================================================
# PHASE 2: PREPARE SUPABASE (optional)
# ================================================================

function Prepare-Supabase {
    Write-Header "PHASE 2: Preparing Supabase for data import"
    
    Write-Info "Connecting to Supabase: $SUPABASE_HOST"
    Write-Info "Note: Supabase may already have initialized schemas"
    Write-Info "If restore fails due to existing objects, clear schema first"
    
    try {
        $env:PGPASSWORD = $supabasePassword
        
        # Optional: Drop all tables if they exist (CAREFUL!)
        $dropSchemaSQL = @"
-- WARNING: This drops all tables! Uncomment only if necessary.
-- DROP SCHEMA public CASCADE;
-- CREATE SCHEMA public;
"@
        
        Write-Info "Connect to Supabase manually if you need to clear the schema:"
        Write-Info "  psql -h $SUPABASE_HOST -U $SUPABASE_USER -d $SUPABASE_DB"
        
        Write-Success "Supabase preparation complete (manual review recommended)"
    }
    finally {
        Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
    }
}

# ================================================================
# PHASE 3: RESTORE TO SUPABASE
# ================================================================

function Restore-ToSupabase {
    param([string]$backupFilePath)
    
    Write-Header "PHASE 3: Restoring data to Supabase PostgreSQL"
    
    if (-not (Test-Path $backupFilePath)) {
        Write-Error-Custom "Backup file not found: $backupFilePath"
        exit 1
    }
    
    $fileSize = (Get-Item $backupFilePath).Length / 1MB
    Write-Info "Backup file: $backupFilePath"
    Write-Info "File size: $([math]::Round($fileSize, 2)) MB"
    
    Write-Info "Connecting to Supabase: $SUPABASE_HOST"
    Write-Info "Database: $SUPABASE_DB"
    Write-Info "User: $SUPABASE_USER"
    Write-Info "Starting restore... This may take several minutes."
    
    try {
        $env:PGPASSWORD = $supabasePassword
        
        # Read backup file and pipe to psql
        $backupContent = Get-Content $backupFilePath -Raw
        
        $backupContent | & psql `
            -h $SUPABASE_HOST `
            -p $SUPABASE_PORT `
            -U $SUPABASE_USER `
            -d $SUPABASE_DB `
            --no-password `
            --verbose
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Restore completed successfully"
        } else {
            Write-Error-Custom "psql restore failed with exit code: $LASTEXITCODE"
            Write-Error-Custom "This might be due to:"
            Write-Error-Custom "  1. Schema already exists - drop it manually in Supabase dashboard"
            Write-Error-Custom "  2. Missing permissions - verify user has correct access"
            Write-Error-Custom "  3. Connection timeout - try again or increase timeout"
            exit 1
        }
    }
    finally {
        Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
    }
}

# ================================================================
# PHASE 4: VERIFY DATA INTEGRITY
# ================================================================

function Verify-DataIntegrity {
    Write-Header "PHASE 4: Verifying data integrity"
    
    try {
        $env:PGPASSWORD = $supabasePassword
        
        # Count tables
        Write-Info "Checking table count..."
        $tableCountResult = & psql `
            -h $SUPABASE_HOST `
            -p $SUPABASE_PORT `
            -U $SUPABASE_USER `
            -d $SUPABASE_DB `
            --no-password `
            -t `
            -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';"
        
        $tableCount = $tableCountResult.Trim()
        Write-Success "Tables in Supabase: $tableCount"
        
        if ($tableCount -eq 0) {
            Write-Error-Custom "No tables found! Restore may have failed."
            exit 1
        }
        
        # List all tables
        Write-Info "Tables in Supabase:"
        $tableList = & psql `
            -h $SUPABASE_HOST `
            -p $SUPABASE_PORT `
            -U $SUPABASE_USER `
            -d $SUPABASE_DB `
            --no-password `
            -t `
            -c "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;"
        
        $tableList | ForEach-Object {
            if ($_.Trim()) {
                Write-Host "  - $_"
            }
        }
        
        # Sample row counts from major tables
        Write-Info "Sample row counts:"
        $tables = @("tenant", "user", "invoice", "sale", "customer", "product", "employee")
        
        foreach ($table in $tables) {
            $countResult = & psql `
                -h $SUPABASE_HOST `
                -p $SUPABASE_PORT `
                -U $SUPABASE_USER `
                -d $SUPABASE_DB `
                --no-password `
                -t `
                -c "SELECT COUNT(*) FROM $table;" 2>$null
            
            if ($LASTEXITCODE -eq 0) {
                $count = $countResult.Trim()
                Write-Host "  - ${table}: $count rows"
            }
        }
        
        # Check for foreign key constraints
        Write-Info "Checking foreign key constraints..."
        $fkResult = & psql `
            -h $SUPABASE_HOST `
            -p $SUPABASE_PORT `
            -U $SUPABASE_USER `
            -d $SUPABASE_DB `
            --no-password `
            -t `
            -c "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_type='FOREIGN KEY';"
        
        $fkCount = $fkResult.Trim()
        Write-Success "Foreign key constraints: $fkCount"
        
        Write-Success "Data integrity verification complete!"
    }
    finally {
        Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
    }
}

# ================================================================
# MAIN EXECUTION
# ================================================================

function Main {
    Write-Header "BIASHARA: Neon to Supabase Data Migration"
    Write-Info "Start time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    
    # Step 1: Create backup directory
    Create-BackupDir
    
    # Step 2: Backup from Neon
    if (-not $skipBackup) {
        $backupPath = Backup-FromNeon
    } else {
        $backupPath = Join-Path $backupDir $backupFile
    }
    
    # Step 3: Prepare Supabase
    Prepare-Supabase
    
    # Step 4: Restore to Supabase
    if (-not $skipRestore) {
        Restore-ToSupabase -backupFilePath $backupPath
    }
    
    # Step 5: Verify data
    if (-not $skipVerify) {
        Verify-DataIntegrity
    }
    
    Write-Header "Migration Complete!"
    Write-Success "End time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    Write-Info "Next steps:"
    Write-Info "  1. Update .env file with Supabase credentials (already done)"
    Write-Info "  2. Update SPRING_PROFILES_ACTIVE=supabase in .env"
    Write-Info "  3. Restart the BIASHARA application"
    Write-Info "  4. Test all functionality"
    Write-Info "  5. Monitor Supabase dashboard for performance"
}

# Run main function
Main
